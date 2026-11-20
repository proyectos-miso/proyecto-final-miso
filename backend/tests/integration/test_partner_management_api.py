"""Gestión de socios desde el portal de Solventa (HOTFIX-gestion-de-socios, FE-WEB-05)."""
import asyncio
import hashlib
import json
import re
import uuid

import pytest
from httpx import AsyncClient
from sqlalchemy import func, select

from app.core.security import create_access_token, verify_password
from app.modules.payments.models import PaymentTransactionDB
from app.modules.policy.models import PartnerPolicyIssueRequest, PolicyDB
from app.modules.policy.service import policy_service
from app.modules.rating import partners
from app.modules.rating.models import PartnerDB
from tests.integration.otp_fixtures import signature_code
from tests.integration.policy_search_fixtures import (
    ALIADO,
    BENEFICIARIES,
    REVOCADO,
    headers,
    seed_partners_and_users,
)

pytestmark = pytest.mark.integration

ANALISTA = "operaciones@solventa.example"
DEL_ALIADO = "analista@bancoaliado.example"
PARTNERS = "/api/v1/experience/partners"
TOKEN = "/api/v1/quote-edge/auth/token"
ADVISOR_POLICIES = "/api/v1/experience/policies"
PARTNER_BENEFICIARIES = [{"name": "Beneficiario de Prueba", "relationship": "Hijo", "percentage": 100}]
QUOTE = {
    "document_type": "CC",
    "document_number": "1020304050",
    "birth_date": "1990-05-15",
    "insured_amount": 100000000.0,
    "city": "Bogota",
    "occupation_risk": 1,
}


@pytest.fixture
async def seeded(postgres_session):
    await seed_partners_and_users(postgres_session)
    return postgres_session


async def register(client: AsyncClient, name: str = "Fintech de Prueba") -> dict:
    response = await client.post(
        "/api/v1/quote-edge/partners/register",
        headers=headers(ANALISTA),
        json={"name": name, "contact_email": "soporte@fintechprueba.example.com"},
    )
    assert response.status_code == 201
    assert response.headers["cache-control"] == "no-store"
    return response.json()


async def partner_token(client: AsyncClient, client_id: str, client_secret: str) -> str:
    response = await client.post(TOKEN, json={"client_id": client_id, "client_secret": client_secret})
    assert response.status_code == 200
    return response.json()["access_token"]


async def quote(client: AsyncClient, token: str):
    return await client.post(
        "/api/v1/quote-edge/quotes",
        headers={"Authorization": f"Bearer {token}", "Idempotency-Key": f"key-{uuid.uuid4().hex}"},
        json=QUOTE,
    )


async def test_el_listado_no_trae_secretos_ni_el_correo_completo(async_client: AsyncClient, seeded):
    created = await register(async_client)

    response = await async_client.get(PARTNERS, headers=headers(ANALISTA))

    assert response.status_code == 200
    assert response.headers["cache-control"] == "no-store"
    body = response.json()
    assert body["truncated"] is False
    by_id = {item["partner_id"]: item for item in body["items"]}
    assert {ALIADO, REVOCADO, created["partner_id"]} <= set(by_id)
    nuevo = by_id[created["partner_id"]]
    assert nuevo["environment"] == "sandbox"
    assert nuevo["status"] == "ACTIVE"
    assert nuevo["quota_limit"] == 1000
    assert nuevo["has_contact"] is True
    assert nuevo["contact_email_masked"] == "s***@fintechprueba.example.com"
    assert by_id[ALIADO]["has_contact"] is False
    assert by_id[REVOCADO]["status"] == "REVOKED"
    text = response.text
    assert "soporte@" not in text
    assert created["client_secret"] not in text
    assert "client_secret" not in text and "hash" not in text


async def test_el_entorno_en_mayusculas_se_lee_en_minusculas(async_client: AsyncClient, seeded):
    partner = await seeded.get(PartnerDB, ALIADO)
    partner.environment = "SANDBOX"
    await seeded.commit()

    response = await async_client.get(PARTNERS, headers=headers(ANALISTA))

    assert {item["partner_id"]: item for item in response.json()["items"]}[ALIADO]["environment"] == "sandbox"


async def test_el_analista_de_un_socio_recibe_403_y_sin_sesion_401(async_client: AsyncClient, seeded):
    calls = [
        ("get", PARTNERS),
        ("post", f"{PARTNERS}/{ALIADO}/revoke"),
        ("post", f"{PARTNERS}/{ALIADO}/credentials"),
        ("post", f"{PARTNERS}/{ALIADO}/approve"),
    ]
    for method, url in calls:
        from_partner = await async_client.request(method, url, headers=headers(DEL_ALIADO))
        anonymous = await async_client.request(method, url)
        advisor = await async_client.request(method, url, headers=headers(ANALISTA, role="advisor"))
        assert from_partner.status_code == 403, url
        assert anonymous.status_code == 401, url
        assert advisor.status_code == 403, url
    partner = await seeded.scalar(select(PartnerDB).where(PartnerDB.partner_id == ALIADO))
    await seeded.refresh(partner)
    assert partner.status == "ACTIVE"


async def test_un_socio_inexistente_da_404(async_client: AsyncClient, seeded):
    for action in ("revoke", "credentials", "approve"):
        response = await async_client.post(f"{PARTNERS}/no-existe-1/{action}", headers=headers(ANALISTA))
        assert response.status_code == 404


async def test_revocar_corta_un_token_ya_emitido_y_es_idempotente(async_client: AsyncClient, seeded):
    created = await register(async_client)
    token = await partner_token(async_client, created["client_id"], created["client_secret"])
    assert (await quote(async_client, token)).status_code == 200

    first = await async_client.post(f"{PARTNERS}/{created['partner_id']}/revoke", headers=headers(ANALISTA))
    again = await async_client.post(f"{PARTNERS}/{created['partner_id']}/revoke", headers=headers(ANALISTA))

    assert first.status_code == 200 and first.json()["status"] == "REVOKED"
    assert again.status_code == 200 and again.json()["status"] == "REVOKED"
    assert (await quote(async_client, token)).status_code == 401
    login = await async_client.post(TOKEN, json={"client_id": created["client_id"], "client_secret": created["client_secret"]})
    assert login.status_code == 401


async def test_rotar_invalida_el_secreto_anterior_y_el_nuevo_sirve(async_client: AsyncClient, seeded, caplog):
    created = await register(async_client)
    caplog.set_level("WARNING", logger="solventa.security.partner")

    response = await async_client.post(f"{PARTNERS}/{created['partner_id']}/credentials", headers=headers(ANALISTA))

    assert response.status_code == 200
    assert response.headers["cache-control"] == "no-store"
    rotated = response.json()
    assert rotated["client_id"] == created["client_id"]
    assert rotated["client_secret"].startswith("sec_")
    assert rotated["client_secret"] != created["client_secret"]
    old = await async_client.post(TOKEN, json={"client_id": created["client_id"], "client_secret": created["client_secret"]})
    assert old.status_code == 401
    token = await partner_token(async_client, rotated["client_id"], rotated["client_secret"])
    assert (await quote(async_client, token)).status_code == 200
    partner = await seeded.scalar(select(PartnerDB).where(PartnerDB.partner_id == created["partner_id"]))
    await seeded.refresh(partner)
    assert partner.client_secret_hash.startswith("$2")
    assert rotated["client_secret"] not in partner.client_secret_hash
    events = caplog.text
    assert "PARTNER_CREDENTIALS_ROTATED" in events
    assert rotated["client_secret"] not in events and created["client_secret"] not in events
    assert "soporte@" not in events


async def test_un_socio_revocado_no_se_rota_ni_se_aprueba(async_client: AsyncClient, seeded):
    rotate = await async_client.post(f"{PARTNERS}/{REVOCADO}/credentials", headers=headers(ANALISTA))
    approve = await async_client.post(f"{PARTNERS}/{REVOCADO}/approve", headers=headers(ANALISTA), json={"quota_limit": 50000})

    assert rotate.status_code == 409
    assert approve.status_code == 409
    assert approve.json()["detail"] == "Socio revocado."
    partner = await seeded.scalar(select(PartnerDB).where(PartnerDB.partner_id == REVOCADO))
    await seeded.refresh(partner)
    assert partner.status == "REVOKED"
    assert partner.quota_limit == 1000


async def test_un_socio_en_sandbox_cotiza_pero_no_emite_y_aprobado_si(async_client: AsyncClient, seeded):
    created = await register(async_client)
    token = await partner_token(async_client, created["client_id"], created["client_secret"])
    quoted = await quote(async_client, token)
    assert quoted.status_code == 200
    assert quoted.headers["X-RateLimit-Limit"] == "1000"
    issue = {
        "quote_id": quoted.json()["quote_id"],
        "tier": "standard",
        "beneficiaries": [{"name": "Beneficiario de Prueba", "relationship": "Hijo", "percentage": 100}],
    }

    blocked = await async_client.post("/api/v1/quote-edge/policies", headers={"Authorization": f"Bearer {token}"}, json=issue)

    assert blocked.status_code == 403
    assert blocked.json()["detail"] == "Un socio en sandbox no emite pólizas reales"
    assert await policy_of(seeded, issue["quote_id"]) is None
    approved = await async_client.post(f"{PARTNERS}/{created['partner_id']}/approve", headers=headers(ANALISTA))
    assert approved.status_code == 200
    issued = await async_client.post("/api/v1/quote-edge/policies", headers={"Authorization": f"Bearer {token}"}, json=issue)
    assert issued.status_code == 200


async def test_el_portal_sabe_si_el_analista_es_de_solventa(async_client: AsyncClient, seeded):
    solventa = await async_client.get("/api/v1/experience/operator", headers=headers(ANALISTA))
    del_socio = await async_client.get("/api/v1/experience/operator", headers=headers(DEL_ALIADO))
    anonymous = await async_client.get("/api/v1/experience/operator")
    advisor = await async_client.get("/api/v1/experience/operator", headers=headers(ANALISTA, role="advisor"))

    assert solventa.status_code == 200 and solventa.json() == {"solventa": True}
    assert del_socio.status_code == 200 and del_socio.json() == {"solventa": False}
    assert anonymous.status_code == 401
    assert advisor.status_code == 403


async def test_un_nombre_con_tildes_da_un_id_ascii_que_se_puede_gestionar(async_client: AsyncClient, seeded):
    created = await register(async_client, "Seguros Bolívar Compañía")

    assert re.fullmatch(r"segurosbolivarcompan_[0-9a-f]{8}", created["partner_id"])
    base = f"{PARTNERS}/{created['partner_id']}"
    assert (await async_client.post(f"{base}/credentials", headers=headers(ANALISTA))).status_code == 200
    assert (await async_client.post(f"{base}/approve", headers=headers(ANALISTA))).status_code == 200
    assert (await async_client.post(f"{base}/revoke", headers=headers(ANALISTA))).status_code == 200


async def test_un_socio_creado_con_tildes_en_el_id_se_puede_revocar(async_client: AsyncClient, seeded):
    seeded.add(PartnerDB(
        partner_id="compañía_0a1b2c3d", client_id="client_compania_viejo", client_secret_hash="no-aplica",
        name="Compañía Antigua", status="ACTIVE", quota_limit=1000, quota_used=0, environment="sandbox",
    ))
    await seeded.commit()

    response = await async_client.post(f"{PARTNERS}/compañía_0a1b2c3d/revoke", headers=headers(ANALISTA))

    assert response.status_code == 200
    assert response.json()["status"] == "REVOKED"


async def test_los_eventos_de_gestion_dicen_quien_actuo_sin_secretos_ni_correos(
    async_client: AsyncClient, seeded, caplog
):
    caplog.set_level("WARNING", logger="solventa.security.partner")
    created = await register(async_client)
    base = f"{PARTNERS}/{created['partner_id']}"
    rotated = (await async_client.post(f"{base}/credentials", headers=headers(ANALISTA))).json()
    await async_client.post(f"{base}/approve", headers=headers(ANALISTA))
    await async_client.post(f"{base}/revoke", headers=headers(ANALISTA))

    events = {}
    for record in caplog.records:
        if record.name == "solventa.security.partner":
            raw = record.getMessage()
            events[json.loads(raw)["event_code"]] = (json.loads(raw), raw)
    expected_ref = hashlib.sha256(ANALISTA.encode()).hexdigest()[:12]
    for code in ("PARTNER_REGISTERED_BY_OPERATOR", "PARTNER_CREDENTIALS_ROTATED",
                 "PARTNER_APPROVED_BY_OPERATOR", "PARTNER_REVOKED_BY_OPERATOR"):
        event, raw = events[code]
        assert event["operator_ref"] == expected_ref, code
        assert event["partner_id"] == created["partner_id"], code
        assert created["client_secret"] not in raw and rotated["client_secret"] not in raw, code
        assert "@" not in raw, code


async def test_dos_rotaciones_simultaneas_dejan_valido_solo_el_ultimo_secreto(async_client: AsyncClient, seeded):
    created = await register(async_client)
    url = f"{PARTNERS}/{created['partner_id']}/credentials"

    responses = await asyncio.gather(*(async_client.post(url, headers=headers(ANALISTA)) for _ in range(2)))

    assert [r.status_code for r in responses] == [200, 200]
    returned = [r.json()["client_secret"] for r in responses]
    assert returned[0] != returned[1]
    partner = await seeded.get(PartnerDB, created["partner_id"])
    await seeded.refresh(partner)
    valid = [secret for secret in returned if verify_password(secret, partner.client_secret_hash)]
    assert len(valid) == 1
    logins = [
        (await async_client.post(TOKEN, json={"client_id": created["client_id"], "client_secret": secret})).status_code
        for secret in returned
    ]
    assert logins[returned.index(valid[0])] == 200
    assert sorted(logins) == [200, 401]


async def test_el_listado_avisa_cuando_queda_truncado(async_client: AsyncClient, seeded, monkeypatch):
    monkeypatch.setattr(partners, "LIST_LIMIT", 2)

    body = (await async_client.get(PARTNERS, headers=headers(ANALISTA))).json()

    assert body["truncated"] is True
    assert len(body["items"]) == 2


async def policy_of(session, quote_id: str):
    return await session.scalar(select(PolicyDB).where(PolicyDB.quote_id == quote_id))


async def partner_quote_id(client: AsyncClient, created: dict) -> str:
    response = await quote(client, await partner_token(client, created["client_id"], created["client_secret"]))
    assert response.status_code == 200
    return response.json()["quote_id"]


async def test_el_asesor_no_emite_la_cotizacion_de_un_socio_en_sandbox(async_client: AsyncClient, seeded):
    created = await register(async_client)
    body = {"quote_id": await partner_quote_id(async_client, created), "tier": "standard",
            "beneficiaries": BENEFICIARIES, "otp_code": "123456"}

    response = await async_client.post(ADVISOR_POLICIES, headers=headers(ANALISTA, role="advisor"), json=body)

    assert response.status_code == 403
    assert response.json()["detail"] == "Un socio en sandbox no emite pólizas reales"
    assert await policy_of(seeded, body["quote_id"]) is None


async def test_un_asesor_de_un_socio_revocado_no_emite(async_client: AsyncClient, seeded):
    created = await register(async_client)
    approved = await async_client.post(f"{PARTNERS}/{created['partner_id']}/approve", headers=headers(ANALISTA))
    assert approved.status_code == 200
    body = {"quote_id": await partner_quote_id(async_client, created), "tier": "standard",
            "beneficiaries": BENEFICIARIES, "otp_code": "123456"}
    token = create_access_token(subject="asesor@revocado.example", role="advisor", claims={"partner_id": REVOCADO})

    response = await async_client.post(ADVISOR_POLICIES, headers={"Authorization": f"Bearer {token}"}, json=body)

    assert response.status_code == 403
    assert response.json()["detail"] == "Socio revocado o inexistente"
    assert await policy_of(seeded, body["quote_id"]) is None


async def test_no_se_cobra_la_poliza_de_un_socio_revocado(async_client: AsyncClient, seeded):
    created = await register(async_client)
    approved = await async_client.post(f"{PARTNERS}/{created['partner_id']}/approve", headers=headers(ANALISTA))
    assert approved.status_code == 200
    token = await partner_token(async_client, created["client_id"], created["client_secret"])
    quoted = await quote(async_client, token)
    issued = await async_client.post(
        "/api/v1/quote-edge/policies",
        headers={"Authorization": f"Bearer {token}"},
        json={"quote_id": quoted.json()["quote_id"], "tier": "standard", "beneficiaries": PARTNER_BENEFICIARIES},
    )
    assert issued.status_code == 200
    policy = issued.json()
    revoked = await async_client.post(f"{PARTNERS}/{created['partner_id']}/revoke", headers=headers(ANALISTA))
    assert revoked.status_code == 200

    response = await async_client.post(
        "/api/v1/experience/payments/charge",
        headers={**headers(ANALISTA, role="advisor"), "Idempotency-Key": "cobro-socio-revocado"},
        json={"policy_number": policy["policy_number"], "amount": policy["conditions"]["monthly_premium"],
              "payment_method": "PSE", "token_or_account": "tok_pse_prueba"},
    )

    assert response.status_code == 403
    assert response.json()["detail"] == "Socio revocado o inexistente"
    charged = await seeded.scalar(
        select(PaymentTransactionDB).where(PaymentTransactionDB.policy_number == policy["policy_number"])
    )
    assert charged is None


def blocked_events(caplog) -> list:
    return [
        json.loads(record.getMessage()) for record in caplog.records
        if record.name == "solventa.security.partner"
        and json.loads(record.getMessage())["event_code"].endswith("ISSUE_BLOCKED")
    ]


async def test_los_bloqueos_dejan_su_evento_de_seguridad_sin_poliza_ni_cobro(async_client: AsyncClient, seeded, caplog):
    created = await register(async_client)
    quote_id = await partner_quote_id(async_client, created)
    body = {"quote_id": quote_id, "tier": "standard", "beneficiaries": BENEFICIARIES, "otp_code": "123456"}
    caplog.set_level("WARNING", logger="solventa.security.partner")

    sandbox = await async_client.post(ADVISOR_POLICIES, headers=headers(ANALISTA, role="advisor"), json=body)
    token = create_access_token(subject="asesor@revocado.example", role="advisor", claims={"partner_id": REVOCADO})
    revoked = await async_client.post(ADVISOR_POLICIES, headers={"Authorization": f"Bearer {token}"}, json=body)

    assert sandbox.status_code == 403 and revoked.status_code == 403
    events = blocked_events(caplog)
    assert [e["event_code"] for e in events] == ["PARTNER_SANDBOX_ISSUE_BLOCKED", "PARTNER_ISSUE_BLOCKED"]
    assert events[0]["partner_id"] == created["partner_id"]
    assert events[0]["client_id"] == created["client_id"]
    assert events[1]["partner_id"] == REVOCADO
    assert all(created["client_secret"] not in json.dumps(e) and "@" not in json.dumps(e) for e in events)
    assert await policy_of(seeded, quote_id) is None
    assert await seeded.scalar(select(PaymentTransactionDB)) is None


async def test_repetir_la_emision_tras_revocar_al_socio_devuelve_la_misma_poliza(async_client: AsyncClient, seeded):
    created = await register(async_client)
    approved = await async_client.post(f"{PARTNERS}/{created['partner_id']}/approve", headers=headers(ANALISTA))
    assert approved.status_code == 200
    advisor = headers(ANALISTA, role="advisor")
    body = {"quote_id": await partner_quote_id(async_client, created), "tier": "standard",
            "beneficiaries": BENEFICIARIES, "otp_code": await signature_code(async_client, advisor)}
    first = await async_client.post(ADVISOR_POLICIES, headers=advisor, json=body)
    assert first.status_code == 200
    revoked = await async_client.post(f"{PARTNERS}/{created['partner_id']}/revoke", headers=headers(ANALISTA))
    assert revoked.status_code == 200

    again = await async_client.post(ADVISOR_POLICIES, headers=advisor, json=body)
    from_partner = await policy_service.issue_partner_policy(
        seeded,
        PartnerPolicyIssueRequest(quote_id=body["quote_id"], tier="standard", beneficiaries=PARTNER_BENEFICIARIES),
        created["partner_id"],
    )

    assert again.status_code == 200
    assert again.json()["policy_number"] == first.json()["policy_number"]
    assert from_partner.policy_number == first.json()["policy_number"]
    count = await seeded.scalar(select(func.count()).select_from(PolicyDB).where(PolicyDB.quote_id == body["quote_id"]))
    assert count == 1
