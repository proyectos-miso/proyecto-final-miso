"""Recepción del reporte del siniestro desde la app contra PostgreSQL y el emulador de S3 (HU-MOV-09)."""
import asyncio
import hashlib
import json
import re
import uuid
from datetime import datetime, timedelta, timezone

import pytest
from httpx import AsyncClient
from sqlalchemy import func, select

from app.core.security import create_access_token, decode_access_token
from app.core.storage import StorageUnavailableError, get_document_storage
from app.main import app
from app.modules.claims.models import ClaimDB, ClaimEventDB, ClaimPhotoDB
from app.modules.identity.db_models import InsuredAccountDB
from app.modules.identity.service import insured_auth_service
from tests.integration.claims_fixtures import JPEG, bucket
from tests.integration.test_insured_policies_api import MARIA_DOCUMENT, OTHER_DOCUMENT, add_policy, insured_headers

pytestmark = [pytest.mark.integration, pytest.mark.usefixtures("seeded_insured")]

URL = "/api/v1/insured/claims"
SOON = datetime.now(timezone.utc) + timedelta(days=200)


@pytest.fixture
async def storage():
    s3 = await bucket()
    app.dependency_overrides[get_document_storage] = lambda: s3
    yield s3
    app.dependency_overrides.pop(get_document_storage, None)


@pytest.fixture
async def policy(postgres_session):
    await add_policy(postgres_session, "POL-2026-12001", MARIA_DOCUMENT, "ACTIVE", SOON)
    return "POL-2026-12001"


def metadata(position: int, content: bytes = JPEG) -> dict:
    return {
        "position": position, "sha256": hashlib.sha256(content).hexdigest(),
        "captured_at": "2026-11-10T14:30:00Z", "trusted_time_source": "GNSS",
        "latitude": 4.6097, "longitude": -74.0817, "accuracy_m": 12.5, "mock_location": False,
    }


def report_body(policy_number: str, report_id: str = None, photos=None) -> dict:
    return {
        "report_id": report_id or str(uuid.uuid4()), "policy_number": policy_number,
        "description": "Choque leve en un parqueadero, sin heridos.",
        "photos": photos if photos is not None else [metadata(1), metadata(2)],
    }


async def send(client: AsyncClient, body: dict, files=None, headers=None):
    files = files if files is not None else {f"photo_{p['position']}": ("foto.jpg", JPEG, "image/jpeg") for p in body["photos"]}
    return await client.post(URL, headers=headers or insured_headers(), data={"report": json.dumps(body)}, files=files)


async def claims_count(session) -> int:
    return await session.scalar(select(func.count()).select_from(ClaimDB))


async def report_token(session) -> tuple:
    account = await session.get(InsuredAccountDB, "ins-0001")
    tokens = insured_auth_service._issue_tokens(account)
    return tokens, {"Authorization": f"Bearer {tokens.report_token}"}


async def test_registra_el_reporte_con_radicado_y_guarda_las_fotos_verificadas(async_client, postgres_session, storage, policy):
    body = report_body(policy)

    response = await send(async_client, body)

    assert response.status_code == 201
    claim_number = response.json()["claim_number"]
    assert re.fullmatch(r"SIN-\d{4}-\d{5}", claim_number)
    claim = await postgres_session.get(ClaimDB, claim_number)
    assert claim.client_report_id == body["report_id"] and claim.status == "REPORTED" and claim.origin == "APP"
    photos = list(await postgres_session.scalars(select(ClaimPhotoDB).where(ClaimPhotoDB.claim_number == claim_number)))
    assert sorted(p.position for p in photos) == [1, 2]
    assert await storage.get_bytes(photos[0].s3_key) == JPEG
    assert await postgres_session.scalar(select(ClaimEventDB.kind).where(ClaimEventDB.claim_number == claim_number)) == "REPORTED"


async def test_el_mismo_reporte_enviado_otra_vez_devuelve_el_mismo_siniestro(async_client, postgres_session, storage, policy):
    body = report_body(policy)

    first = await send(async_client, body)
    again = await send(async_client, body)

    assert (first.status_code, again.status_code) == (201, 200)
    assert again.json()["claim_number"] == first.json()["claim_number"]
    assert await claims_count(postgres_session) == 1


async def test_dos_envios_simultaneos_del_mismo_reporte_dejan_un_siniestro(async_client, postgres_session, storage, policy):
    body = report_body(policy)

    responses = await asyncio.gather(send(async_client, body), send(async_client, body))

    assert sorted(r.status_code for r in responses) == [200, 201]
    assert len({r.json()["claim_number"] for r in responses}) == 1
    assert await claims_count(postgres_session) == 1


async def test_dos_reportes_distintos_reciben_radicados_distintos(async_client, postgres_session, storage, policy):
    first = await send(async_client, report_body(policy))
    second = await send(async_client, report_body(policy))

    assert first.json()["claim_number"] != second.json()["claim_number"]


async def test_una_foto_alterada_se_rechaza_y_no_queda_nada(async_client, postgres_session, storage, policy):
    body = report_body(policy)
    files = {"photo_1": ("foto.jpg", JPEG, "image/jpeg"), "photo_2": ("foto.jpg", JPEG + b"editada", "image/jpeg")}

    response = await send(async_client, body, files=files)

    assert response.status_code == 422
    assert "cambió después de tomarla" in response.json()["detail"]
    assert await claims_count(postgres_session) == 0


@pytest.mark.parametrize("files, status", [
    ({"photo_1": ("foto.png", b"\x89PNG" + b"x" * 100, "image/png")}, 422),
    ({"photo_1": ("foto.jpg", JPEG + b"x" * 2_000_001, "image/jpeg")}, 413),
    ({}, 422),
], ids=["no-es-jpeg", "mas-de-2-mb", "sin-fotos"])
async def test_fotos_que_no_cumplen_los_limites_se_rechazan(async_client, postgres_session, storage, policy, files, status):
    content = files.get("photo_1", (None, JPEG))[1]
    body = report_body(policy, photos=[metadata(1, content)])

    response = await send(async_client, body, files=files)

    assert response.status_code == status
    assert await claims_count(postgres_session) == 0


@pytest.mark.parametrize("report", [
    "no es json",
    json.dumps({"report_id": str(uuid.uuid4()), "policy_number": "POL-2026-12001", "description": "x" * 501, "photos": [metadata(1)]}),
    json.dumps({"report_id": str(uuid.uuid4()), "policy_number": "POL-2026-12001", "description": "Choque", "photos": [metadata(2)]}),
    json.dumps({"report_id": str(uuid.uuid4()), "policy_number": "POL-2026-12001", "description": "Choque",
                "photos": [metadata(p) for p in range(1, 7)]}),
], ids=["formato", "descripcion-larga", "posiciones", "mas-de-5-fotos"])
async def test_un_reporte_mal_formado_responde_422(async_client, storage, policy, report):
    response = await async_client.post(
        URL, headers=insured_headers(), data={"report": report}, files={"photo_1": ("foto.jpg", JPEG, "image/jpeg")}
    )

    assert response.status_code == 422


async def test_la_poliza_de_otra_persona_o_no_vigente_responde_404(async_client, postgres_session, storage):
    await add_policy(postgres_session, "POL-2026-12002", OTHER_DOCUMENT, "ACTIVE", SOON)
    await add_policy(postgres_session, "POL-2026-12003", MARIA_DOCUMENT, "CANCELLED", SOON)
    await add_policy(postgres_session, "POL-2026-12004", MARIA_DOCUMENT, "ACTIVE", datetime.now(timezone.utc) - timedelta(days=1))

    responses = [await send(async_client, report_body(n)) for n in ("POL-2026-12002", "POL-2026-12003", "POL-2026-12004")]

    assert [r.status_code for r in responses] == [404, 404, 404]
    assert await claims_count(postgres_session) == 0


async def test_s3_caido_responde_503_y_el_reporte_se_puede_reintentar(async_client, postgres_session, storage, policy, monkeypatch):
    body = report_body(policy)

    async def down(*args):
        raise StorageUnavailableError("sin S3")

    monkeypatch.setattr(storage, "put_bytes", down)
    failed = await send(async_client, body)
    monkeypatch.undo()
    retried = await send(async_client, body)

    assert failed.status_code == 503 and failed.headers["retry-after"] == "30"
    assert retried.status_code == 201


async def test_con_la_app_cerrada_el_token_de_reporte_envia_el_reporte(async_client, postgres_session, storage, policy):
    _, headers = await report_token(postgres_session)

    response = await send(async_client, report_body(policy), headers=headers)

    assert response.status_code == 201


async def test_el_token_de_reporte_no_abre_otros_endpoints(async_client, postgres_session, storage, policy):
    _, headers = await report_token(postgres_session)

    assert (await async_client.get(URL, headers=headers)).status_code == 401
    assert (await async_client.get("/api/v1/insured/policies", headers=headers)).status_code == 401


async def test_cerrar_sesion_revoca_el_token_de_reporte(async_client, postgres_session, storage, policy):
    tokens, headers = await report_token(postgres_session)

    closed = await async_client.post("/api/v1/auth/logout", json={"refresh_token": tokens.refresh_token})
    response = await send(async_client, report_body(policy), headers=headers)

    assert closed.status_code == 204
    assert response.status_code == 401


async def test_cerrar_la_app_sin_cerrar_sesion_deja_enviar_el_reporte(async_client, postgres_session, storage, policy):
    tokens, headers = await report_token(postgres_session)

    closed = await async_client.post(
        "/api/v1/auth/logout", json={"refresh_token": tokens.refresh_token, "keep_reporting": True}
    )
    refreshed = await async_client.post("/api/v1/auth/refresh", json={"refresh_token": tokens.refresh_token})
    response = await send(async_client, report_body(policy), headers=headers)

    assert closed.status_code == 204
    assert refreshed.status_code == 401
    assert response.status_code == 201


async def test_la_renovacion_conserva_la_familia_y_el_cierre_de_sesion_revoca_todos_sus_tokens(async_client, postgres_session, storage, policy):
    tokens, old_headers = await report_token(postgres_session)
    renewed = (await async_client.post("/api/v1/auth/refresh", json={"refresh_token": tokens.refresh_token})).json()

    await async_client.post("/api/v1/auth/logout", json={"refresh_token": renewed["refresh_token"]})
    old = await send(async_client, report_body(policy), headers=old_headers)
    new = await send(async_client, report_body(policy), headers={"Authorization": f"Bearer {renewed['report_token']}"})

    assert (old.status_code, new.status_code) == (401, 401)


async def test_cambiar_la_contrasena_revoca_el_token_de_reporte(async_client, postgres_session, storage, policy):
    _, headers = await report_token(postgres_session)
    account = await postgres_session.get(InsuredAccountDB, "ins-0001")
    account.token_version += 1
    await postgres_session.commit()

    response = await send(async_client, report_body(policy), headers=headers)

    assert response.status_code == 401


async def test_sin_token_o_con_token_del_portal_se_rechaza(async_client, storage, policy):
    advisor = {"Authorization": f"Bearer {create_access_token(subject='asesor@solventa.com', role='advisor')}"}

    anonymous = await async_client.post(URL, data={"report": json.dumps(report_body(policy))})
    portal = await send(async_client, report_body(policy), headers=advisor)

    assert anonymous.status_code == 401
    assert portal.status_code == 403


async def test_el_reporte_de_otra_persona_con_el_mismo_identificador_no_se_entrega(async_client, postgres_session, storage, policy):
    body = report_body(policy)
    await send(async_client, body)
    await add_policy(postgres_session, "POL-2026-12005", OTHER_DOCUMENT, "ACTIVE", SOON)
    await insured_auth_service.register(
        postgres_session, user_id="ins-0002", email="pedro.prueba@example.com", full_name="Pedro Prueba",
        document_number=OTHER_DOCUMENT, phone="+573000000002", password="Clave-De-Prueba-2026",
    )
    other = {"Authorization": f"Bearer {create_access_token(subject='ins-0002', role='insured', claims={'type': 'access', 'ver': 0})}"}

    response = await send(async_client, {**body, "policy_number": "POL-2026-12005"}, headers=other)

    assert response.status_code == 404
    assert await claims_count(postgres_session) == 1


async def test_el_ingreso_entrega_un_token_de_reporte_atado_a_su_refresh(postgres_session):
    tokens, _ = await report_token(postgres_session)

    report = decode_access_token(tokens.report_token)
    refresh = decode_access_token(tokens.refresh_token)

    assert report["type"] == "report" and report["scope"] == "claims:submit"
    assert report["sid"] == refresh["sid"]
    remaining = report["exp"] - datetime.now(timezone.utc).timestamp()
    assert 6 * 24 * 3600 < remaining <= 7 * 24 * 3600


async def test_reportes_distintos_enviados_al_mismo_tiempo_reciben_radicados_distintos(async_client, postgres_session, storage, policy):
    responses = await asyncio.gather(*(send(async_client, report_body(policy)) for _ in range(5)))

    assert [r.status_code for r in responses] == [201] * 5
    assert len({r.json()["claim_number"] for r in responses}) == 5
    assert await claims_count(postgres_session) == 5


async def test_un_reporte_de_mas_de_11_mb_responde_413_antes_de_leer_el_multipart(
    async_client, postgres_session, storage, policy
):
    photos = {f"photo_{p}": ("foto.jpg", JPEG + b"0" * 2_300_000, "image/jpeg") for p in range(1, 6)}
    big = await send(async_client, report_body(policy, photos=[metadata(p) for p in range(1, 6)]), files=photos)

    async def chunks():
        yield b'--x\r\nContent-Disposition: form-data; name="photo_1"; filename="f.jpg"\r\nContent-Type: image/jpeg\r\n\r\n'
        for _ in range(12):
            yield b"0" * (1024 * 1024)

    streamed = await async_client.post(
        URL, content=chunks(), headers={**insured_headers(), "Content-Type": "multipart/form-data; boundary=x"}
    )

    assert big.status_code == 413
    assert streamed.status_code == 413
    assert "content-length" not in streamed.request.headers
    assert await claims_count(postgres_session) == 0
