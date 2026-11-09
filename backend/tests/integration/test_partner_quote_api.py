"""Pruebas de integración de los endpoints B2B de Quote Edge (HU-WEB-10)."""
from datetime import timedelta
import json
import logging
import pytest
from httpx import AsyncClient

from app.core.security import (
    create_access_token,
    create_partner_access_token,
    get_password_hash,
)
from app.modules.rating.models import PartnerDB, PartnerStatus


QUOTE_PAYLOAD = {
    "document_type": "CC",
    "document_number": "1020304050",
    "birth_date": "1990-05-15",
    "insured_amount": 150000000.0,
    "city": "Bogota",
    "occupation_risk": 2,
}


async def _obtain_partner_token(
    client: AsyncClient,
    client_id: str = "banco_aliado",
    client_secret: str = "AliadoSecret2026*",
) -> str:
    resp = await client.post(
        "/api/v1/quote-edge/auth/token",
        json={"client_id": client_id, "client_secret": client_secret},
    )
    assert resp.status_code == 200
    return resp.json()["access_token"]


@pytest.mark.integration
@pytest.mark.asyncio
async def test_partner_oauth2_token_issuance_and_security_audit(async_client: AsyncClient, caplog):
    """HU-WEB-10 (#175): emisión de JWT con credenciales válidas y rechazo 401 con auditoría ante inválidas o revocadas."""
    # 1. Emisión exitosa para socio activo
    resp_ok = await async_client.post(
        "/api/v1/quote-edge/auth/token",
        json={"client_id": "banco_aliado", "client_secret": "AliadoSecret2026*"},
    )
    assert resp_ok.status_code == 200
    data = resp_ok.json()
    assert data["token_type"] == "bearer"
    assert data["partner_id"] == "banco-aliado-1"
    assert data["expires_in"] == 3600
    assert len(data["access_token"]) > 20

    # 2. Rechazo por contraseña incorrecta -> 401 + PARTNER_AUTH_FAILED
    with caplog.at_level(logging.WARNING, logger="solventa.security.partner"):
        resp_bad = await async_client.post(
            "/api/v1/quote-edge/auth/token",
            json={"client_id": "banco_aliado", "client_secret": "SecretaErronea123*"},
        )
    assert resp_bad.status_code == 401
    assert json.loads(caplog.records[-1].message)["event_code"] == "PARTNER_AUTH_FAILED"

    # 3. Rechazo por socio revocado -> 401 + PARTNER_REVOKED
    with caplog.at_level(logging.WARNING, logger="solventa.security.partner"):
        resp_revoked = await async_client.post(
            "/api/v1/quote-edge/auth/token",
            json={"client_id": "socio_inactivo", "client_secret": "Inactivo2026*"},
        )
    assert resp_revoked.status_code == 401
    assert json.loads(caplog.records[-1].message)["event_code"] == "PARTNER_REVOKED"


@pytest.mark.integration
@pytest.mark.asyncio
async def test_partner_quote_creation_persists_partner_id_and_increments_quota(
    async_client: AsyncClient,
    test_db_session,
):
    """HU-WEB-10 (#176): cotización B2B persiste bajo el partner_id del socio e incrementa quota_used."""
    token = await _obtain_partner_token(async_client)
    headers = {"Authorization": f"Bearer {token}"}

    resp1 = await async_client.post(
        "/api/v1/quote-edge/quotes",
        headers={**headers, "Idempotency-Key": "test-key-quote-1"},
        json=QUOTE_PAYLOAD,
    )
    resp2 = await async_client.post(
        "/api/v1/quote-edge/quotes",
        headers={**headers, "Idempotency-Key": "test-key-quote-2"},
        json=QUOTE_PAYLOAD,
    )

    assert resp1.status_code == 200
    assert resp2.status_code == 200

    body1 = resp1.json()
    assert body1["partner_id"] == "banco-aliado-1"
    assert body1["document_number"] == "1020304050"
    assert len(body1["tiers"]) == 3
    for tier in body1["tiers"]:
        assert sum(f["amount"] for f in tier["breakdown"]) == tier["monthly_premium"]

    partner = test_db_session.partners_by_id["banco-aliado-1"]
    assert partner.quota_used == 2
    assert test_db_session.quotes[body1["quote_id"]].partner_id == "banco-aliado-1"


@pytest.mark.integration
@pytest.mark.asyncio
async def test_partner_quote_endpoint_rejects_unauthorized_requests_and_logs(
    async_client: AsyncClient,
    caplog,
):
    """HU-WEB-10 (#175, #178): rechaza 401 sin token, token inválido, expirado, de asesor o de socio revocado."""
    expired_token = create_partner_access_token(
        client_id="banco_aliado",
        partner_id="banco-aliado-1",
        expires_delta=timedelta(seconds=-10),
    )
    revoked_token = create_partner_access_token(
        client_id="socio_inactivo",
        partner_id="socio-inactivo-1",
    )
    advisor_token = create_access_token(subject="asesor@solventa.com", role="advisor")

    scenarios = [
        ({}, "PARTNER_TOKEN_MISSING"),
        ({"Authorization": "Bearer token.manipulado.invalido"}, "PARTNER_TOKEN_INVALID"),
        ({"Authorization": f"Bearer {expired_token}"}, "PARTNER_TOKEN_EXPIRED"),
        ({"Authorization": f"Bearer {advisor_token}"}, "PARTNER_ROLE_MISMATCH"),
        ({"Authorization": f"Bearer {revoked_token}"}, "PARTNER_REVOKED"),
    ]

    for headers, expected_code in scenarios:
        caplog.clear()
        with caplog.at_level(logging.WARNING, logger="solventa.security.partner"):
            resp = await async_client.post("/api/v1/quote-edge/quotes", headers=headers, json=QUOTE_PAYLOAD)
        assert resp.status_code == 401
        assert resp.headers.get("WWW-Authenticate") == "Bearer"
        assert len(caplog.records) == 1
        logged = json.loads(caplog.records[0].message)
        assert logged["event_code"] == expected_code
        assert logged["endpoint"] == "/api/v1/quote-edge/quotes"


@pytest.mark.integration
@pytest.mark.asyncio
async def test_partner_quote_isolation_between_partners_and_channels(
    async_client: AsyncClient,
    test_db_session,
):
    """HU-WEB-10 (#177, #178): un socio solo puede consultar y listar sus propias cotizaciones (404 en ajenas)."""
    # Sembrar un segundo socio activo para probar aislamiento cruzado
    second_partner = PartnerDB(
        partner_id="cooperativa-aliada-2",
        client_id="coop_aliada",
        client_secret_hash=get_password_hash("CoopSecret2026*"),
        name="Cooperativa Aliada",
        status=PartnerStatus.ACTIVE.value,
        quota_limit=10000,
        quota_used=0,
    )
    test_db_session.add(second_partner)

    token_partner_1 = await _obtain_partner_token(async_client, "banco_aliado", "AliadoSecret2026*")
    token_partner_2 = await _obtain_partner_token(async_client, "coop_aliada", "CoopSecret2026*")
    advisor_token = create_access_token(subject="asesor@solventa.com", role="advisor")

    # 1. Socio 1 crea 2 cotizaciones
    q1_p1 = (
        await async_client.post(
            "/api/v1/quote-edge/quotes",
            headers={"Authorization": f"Bearer {token_partner_1}", "Idempotency-Key": "iso-key-1"},
            json=QUOTE_PAYLOAD,
        )
    ).json()
    q2_p1 = (
        await async_client.post(
            "/api/v1/quote-edge/quotes",
            headers={"Authorization": f"Bearer {token_partner_1}", "Idempotency-Key": "iso-key-2"},
            json={**QUOTE_PAYLOAD, "document_number": "9090909090"},
        )
    ).json()

    # 2. Socio 2 crea 1 cotización
    q1_p2 = (
        await async_client.post(
            "/api/v1/quote-edge/quotes",
            headers={"Authorization": f"Bearer {token_partner_2}", "Idempotency-Key": "iso-key-3"},
            json=QUOTE_PAYLOAD,
        )
    ).json()

    # 3. Asesor comercial crea 1 cotización en el canal interno (/experience/quotes, partner_id=None)
    q_advisor = (
        await async_client.post(
            "/api/v1/experience/quotes",
            headers={"Authorization": f"Bearer {advisor_token}"},
            json=QUOTE_PAYLOAD,
        )
    ).json()

    # 4. Socio 1 consulta su propia cotización -> 200 OK
    resp_own = await async_client.get(
        f"/api/v1/quote-edge/quotes/{q1_p1['quote_id']}",
        headers={"Authorization": f"Bearer {token_partner_1}"},
    )
    assert resp_own.status_code == 200
    assert resp_own.json()["quote_id"] == q1_p1["quote_id"]
    assert resp_own.json()["partner_id"] == "banco-aliado-1"

    # 5. Socio 1 intenta consultar la cotización del Socio 2 -> 404 Not Found
    resp_other_partner = await async_client.get(
        f"/api/v1/quote-edge/quotes/{q1_p2['quote_id']}",
        headers={"Authorization": f"Bearer {token_partner_1}"},
    )
    assert resp_other_partner.status_code == 404

    # 6. Socio 1 intenta consultar cotización del canal asesor (partner_id=None) -> 404 Not Found
    resp_advisor_quote = await async_client.get(
        f"/api/v1/quote-edge/quotes/{q_advisor['quote_id']}",
        headers={"Authorization": f"Bearer {token_partner_1}"},
    )
    assert resp_advisor_quote.status_code == 404

    # 7. Socio 1 consulta cotización inexistente -> 404 Not Found
    resp_not_found = await async_client.get(
        "/api/v1/quote-edge/quotes/QUO-000000000000",
        headers={"Authorization": f"Bearer {token_partner_1}"},
    )
    assert resp_not_found.status_code == 404

    # 8. Listado de cotizaciones del Socio 1 devuelve únicamente sus 2 cotizaciones en orden descendente
    resp_list_p1 = await async_client.get(
        "/api/v1/quote-edge/quotes",
        headers={"Authorization": f"Bearer {token_partner_1}"},
    )
    assert resp_list_p1.status_code == 200
    items_p1 = resp_list_p1.json()
    assert [item["quote_id"] for item in items_p1] == [q2_p1["quote_id"], q1_p1["quote_id"]]
    assert all(item["partner_id"] == "banco-aliado-1" for item in items_p1)

    # 9. Listado de cotizaciones del Socio 2 devuelve únicamente su cotización
    resp_list_p2 = await async_client.get(
        "/api/v1/quote-edge/quotes",
        headers={"Authorization": f"Bearer {token_partner_2}"},
    )
    assert resp_list_p2.status_code == 200
    items_p2 = resp_list_p2.json()
    assert [item["quote_id"] for item in items_p2] == [q1_p2["quote_id"]]


# -- Pruebas de Telemetría de Cuotas y Rechazo 429 (HU-WEB-12) ---------------------------------


@pytest.mark.integration
@pytest.mark.asyncio
async def test_partner_quote_includes_ratelimit_headers(
    async_client: AsyncClient,
    test_db_session,
):
    """TC-01 (HU-WEB-12.2 / CA-04): cotización exitosa incluye cabeceras X-RateLimit-* de cuota restante."""
    token = await _obtain_partner_token(async_client)
    headers = {"Authorization": f"Bearer {token}", "Idempotency-Key": "test-key-ratelimit-headers"}

    resp = await async_client.post(
        "/api/v1/quote-edge/quotes",
        headers=headers,
        json=QUOTE_PAYLOAD,
    )
    assert resp.status_code == 200
    assert "X-RateLimit-Limit" in resp.headers
    assert "X-RateLimit-Remaining" in resp.headers
    assert "X-RateLimit-Reset" in resp.headers
    assert "X-RateLimit-Reset-Time" in resp.headers

    limit = int(resp.headers["X-RateLimit-Limit"])
    remaining = int(resp.headers["X-RateLimit-Remaining"])
    assert limit == 50000
    assert remaining == 49999
    assert int(resp.headers["X-RateLimit-Reset"]) > 0


@pytest.mark.integration
@pytest.mark.asyncio
async def test_partner_quota_exceeded_returns_429_with_retry_after(
    async_client: AsyncClient,
    test_db_session,
    caplog,
):
    """TC-02 (HU-WEB-12.2 / CA-01, CA-02): cuando el socio supera su cuota, recibe 429 con Retry-After y reset_at."""
    # Configurar socio con cuota límite baja
    partner = test_db_session.partners_by_id["banco-aliado-1"]
    partner.quota_limit = 2
    partner.quota_used = 0

    token = await _obtain_partner_token(async_client)
    headers = {"Authorization": f"Bearer {token}"}

    # 1. Primera cotización -> 200 OK (resta 1)
    resp1 = await async_client.post(
        "/api/v1/quote-edge/quotes",
        headers={**headers, "Idempotency-Key": "quota-key-1"},
        json=QUOTE_PAYLOAD,
    )
    assert resp1.status_code == 200
    assert resp1.headers["X-RateLimit-Remaining"] == "1"

    # 2. Segunda cotización -> 200 OK (resta 0)
    resp2 = await async_client.post(
        "/api/v1/quote-edge/quotes",
        headers={**headers, "Idempotency-Key": "quota-key-2"},
        json=QUOTE_PAYLOAD,
    )
    assert resp2.status_code == 200
    assert resp2.headers["X-RateLimit-Remaining"] == "0"

    # 3. Tercera cotización -> 429 Too Many Requests
    with caplog.at_level(logging.WARNING, logger="solventa.security.partner"):
        resp3 = await async_client.post(
            "/api/v1/quote-edge/quotes",
            headers={**headers, "Idempotency-Key": "quota-key-3"},
            json=QUOTE_PAYLOAD,
        )
    assert resp3.status_code == 429
    assert "Retry-After" in resp3.headers
    retry_after_val = int(resp3.headers["Retry-After"])
    assert retry_after_val > 0
    assert resp3.headers["X-RateLimit-Remaining"] == "0"
    assert resp3.headers["X-RateLimit-Limit"] == "2"

    body3 = resp3.json()
    assert body3["error"] == "QUOTA_EXCEEDED"
    assert "Se ha superado la cuota" in body3["detail"]
    assert body3["retry_after"] == retry_after_val
    assert "reset_at" in body3
    assert body3["quota_limit"] == 2
    assert body3["quota_remaining"] == 0

    # Verificar registro de auditoría de seguridad
    audit_events = [json.loads(r.message) for r in caplog.records if "PARTNER_QUOTA_EXCEEDED" in r.message]
    assert len(audit_events) >= 1
    assert audit_events[0]["event_code"] == "PARTNER_QUOTA_EXCEEDED"
    assert audit_events[0]["partner_id"] == "banco-aliado-1"


@pytest.mark.integration
@pytest.mark.asyncio
async def test_partner_quota_isolation_between_concurrent_partners(
    async_client: AsyncClient,
    test_db_session,
):
    """TC-03 (HU-WEB-12.4 / CA-03 / HA-07): el agotamiento de cuota de un socio no afecta a otro socio concurrent."""
    # Crear segundo socio activo
    second_partner = PartnerDB(
        partner_id="coop-activa-99",
        client_id="coop_activa",
        client_secret_hash=get_password_hash("CoopPass2026*"),
        name="Cooperativa Activa",
        status=PartnerStatus.ACTIVE.value,
        quota_limit=10,
        quota_used=0,
    )
    test_db_session.add(second_partner)

    # Agotar cuota del Socio 1 (Banco Aliado)
    partner1 = test_db_session.partners_by_id["banco-aliado-1"]
    partner1.quota_limit = 1
    partner1.quota_used = 1

    token1 = await _obtain_partner_token(async_client, "banco_aliado", "AliadoSecret2026*")
    token2 = await _obtain_partner_token(async_client, "coop_activa", "CoopPass2026*")

    # Socio 1 cotiza -> 429
    resp_p1 = await async_client.post(
        "/api/v1/quote-edge/quotes",
        headers={"Authorization": f"Bearer {token1}", "Idempotency-Key": "p1-exceeded-key"},
        json=QUOTE_PAYLOAD,
    )
    assert resp_p1.status_code == 429

    # Socio 2 cotiza -> 200 OK con su propia cuota
    resp_p2 = await async_client.post(
        "/api/v1/quote-edge/quotes",
        headers={"Authorization": f"Bearer {token2}", "Idempotency-Key": "p2-normal-key"},
        json=QUOTE_PAYLOAD,
    )
    assert resp_p2.status_code == 200
    assert resp_p2.json()["partner_id"] == "coop-activa-99"
    assert resp_p2.headers["X-RateLimit-Limit"] == "10"
    assert resp_p2.headers["X-RateLimit-Remaining"] == "9"


@pytest.mark.integration
@pytest.mark.asyncio
async def test_partner_idempotent_retry_allowed_when_quota_exhausted(
    async_client: AsyncClient,
    test_db_session,
):
    """TC-04 (HU-WEB-12.2 / AC-05): reintentos con la misma Idempotency-Key retornan 200 aun con cuota en 0."""
    partner = test_db_session.partners_by_id["banco-aliado-1"]
    partner.quota_limit = 1
    partner.quota_used = 0

    token = await _obtain_partner_token(async_client)
    headers = {"Authorization": f"Bearer {token}", "Idempotency-Key": "idemp-quota-key"}

    # 1. Petición inicial consume la única cuota disponible -> 200 OK
    resp1 = await async_client.post(
        "/api/v1/quote-edge/quotes",
        headers=headers,
        json=QUOTE_PAYLOAD,
    )
    assert resp1.status_code == 200
    assert partner.quota_used == 1

    # 2. Reintento con la misma clave y payload -> 200 OK (no descuenta ni bloquea por 429)
    resp_retry = await async_client.post(
        "/api/v1/quote-edge/quotes",
        headers=headers,
        json=QUOTE_PAYLOAD,
    )
    assert resp_retry.status_code == 200
    assert resp_retry.json()["quote_id"] == resp1.json()["quote_id"]
    assert partner.quota_used == 1

    # 3. Petición nueva con clave diferente -> 429 Too Many Requests
    resp_new = await async_client.post(
        "/api/v1/quote-edge/quotes",
        headers={"Authorization": f"Bearer {token}", "Idempotency-Key": "idemp-quota-key-diff"},
        json=QUOTE_PAYLOAD,
    )
    assert resp_new.status_code == 429


@pytest.mark.integration
@pytest.mark.asyncio
async def test_partner_quota_rejection_latency_performance(
    async_client: AsyncClient,
    test_db_session,
):
    """TC-06 (HU-WEB-12 / QR-01): rechazo 429 ultra-rápido en capa de borde sin llamar a buró ni computar tarifas."""
    import time

    partner = test_db_session.partners_by_id["banco-aliado-1"]
    partner.quota_limit = 1
    partner.quota_used = 1

    token = await _obtain_partner_token(async_client)
    headers = {"Authorization": f"Bearer {token}", "Idempotency-Key": "perf-key-rejected"}

    # Con simulated_delay alto para confirmar que no se invoca buró cuando la cuota está agotada
    start = time.perf_counter()
    resp = await async_client.post(
        "/api/v1/quote-edge/quotes?simulated_delay=1.0",
        headers=headers,
        json=QUOTE_PAYLOAD,
    )
    duration_ms = (time.perf_counter() - start) * 1000

    assert resp.status_code == 429
    # Debe responder inmediatamente (< 100 ms) sin esperar el delay de 1.0s del buró
    assert duration_ms < 200.0


@pytest.mark.integration
@pytest.mark.asyncio
async def test_partner_quote_openapi_spec_documents_429(
    async_client: AsyncClient,
):
    """TC-07 (HU-WEB-12.3 / AC-01, AC-02): el contrato OpenAPI documenta 429, cabeceras y esquema de cuota."""
    resp = await async_client.get("/openapi.json")
    assert resp.status_code == 200
    spec = resp.json()

    quotes_post = spec["paths"]["/api/v1/quote-edge/quotes"]["post"]
    responses = quotes_post["responses"]

    assert "429" in responses
    resp_429 = responses["429"]
    assert "headers" in resp_429
    assert "Retry-After" in resp_429["headers"]
    assert "X-RateLimit-Limit" in resp_429["headers"]
    assert "X-RateLimit-Remaining" in resp_429["headers"]
    assert "X-RateLimit-Reset" in resp_429["headers"]
    assert "X-RateLimit-Reset-Time" in resp_429["headers"]

