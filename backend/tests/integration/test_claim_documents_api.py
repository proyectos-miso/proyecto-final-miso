"""Documentos que el asegurado agrega a su siniestro abierto, contra PostgreSQL y el emulador de S3 (mejora FE-MOV-05)."""
import asyncio
import hashlib
import json
import logging
import uuid

import pytest
from httpx import AsyncClient
from sqlalchemy import func, select, update

from app.core.outbox import OutboxMessageDB
from app.core.storage import StorageUnavailableError, get_document_storage
from app.main import app
from app.modules.claims.models import ClaimDB, ClaimDocumentDB, ClaimEventDB
from app.modules.identity.db_models import InsuredAccountDB
from app.modules.identity.service import insured_auth_service
from tests.conftest import INSURED_PASSWORD
from tests.integration.claims_fixtures import JPEG, bucket, report
from tests.integration.policy_search_fixtures import ALIADO, add_policies, headers, policy_row, seed_partners_and_users
from tests.integration.test_insured_policies_api import MARIA_DOCUMENT, OTHER_DOCUMENT, insured_headers

pytestmark = [pytest.mark.integration, pytest.mark.usefixtures("seeded_insured")]

PDF = b"%PDF-1.7\n" + b"documento de prueba del siniestro " * 40 + b"\n%%EOF"
OWN = "SIN-2026-00001"
PARTNER_CLAIM = "SIN-2026-00002"
FOREIGN = "SIN-2026-00003"
CLOSED = "SIN-2026-00004"
ANALISTA = "operaciones@solventa.example"
DEL_ALIADO = "analista@bancoaliado.example"


@pytest.fixture
async def storage():
    s3 = await bucket()
    app.dependency_overrides[get_document_storage] = lambda: s3
    yield s3
    app.dependency_overrides.pop(get_document_storage, None)


@pytest.fixture
async def claims(postgres_session, storage):
    session = postgres_session
    await seed_partners_and_users(session)
    await add_policies(session, [
        policy_row("POL-2026-00001", MARIA_DOCUMENT),
        policy_row("POL-2026-00002", MARIA_DOCUMENT, partner_id=ALIADO),
        policy_row("POL-2026-00003", OTHER_DOCUMENT),
    ])
    await report(session, OWN, "POL-2026-00001")
    await report(session, PARTNER_CLAIM, "POL-2026-00002", minutes=1)
    await report(session, FOREIGN, "POL-2026-00003", minutes=2)
    await report(session, CLOSED, "POL-2026-00001", minutes=3)
    await session.execute(update(ClaimDB).where(ClaimDB.claim_number == CLOSED).values(status="REJECTED"))
    await session.commit()
    return session


def manifest(contents, upload_id: str = None) -> dict:
    return {
        "upload_id": upload_id or str(uuid.uuid4()),
        "pages": [{"position": i, "sha256": hashlib.sha256(c).hexdigest()} for i, c in enumerate(contents, start=1)],
    }


async def upload(client: AsyncClient, claim_number: str, body: dict, contents=None, auth=None, files=None):
    if files is None:
        files = [(f"page_{i}", (f"pagina_{i}", c, "application/octet-stream")) for i, c in enumerate(contents, start=1)]
    return await client.post(
        f"/api/v1/insured/claims/{claim_number}/documents", headers=auth or insured_headers(),
        data={"manifest": json.dumps(body)}, files=files,
    )


async def documents_of(session, claim_number: str) -> list:
    return list(await session.scalars(
        select(ClaimDocumentDB).where(ClaimDocumentDB.claim_number == claim_number).order_by(ClaimDocumentDB.position)
    ))


async def count(session, model) -> int:
    return await session.scalar(select(func.count()).select_from(model))


async def test_agrega_paginas_jpeg_y_pdf_al_siniestro_abierto_del_titular(async_client, claims, storage):
    body = manifest([JPEG, PDF])

    response = await upload(async_client, OWN, body, [JPEG, PDF])

    assert response.status_code == 201
    assert response.json()["pages"] == 2 and response.json()["upload_id"] == body["upload_id"]
    rows = await documents_of(claims, OWN)
    assert [(r.position, r.content_type) for r in rows] == [(1, "image/jpeg"), (2, "application/pdf")]
    assert rows[0].s3_key == f"siniestros/{OWN}/documentos/{body['upload_id']}/1.jpg"
    assert rows[1].s3_key.endswith("/2.pdf")
    assert await storage.get_bytes(rows[0].s3_key) == JPEG
    assert await storage.get_bytes(rows[1].s3_key) == PDF
    kinds = list(await claims.scalars(select(ClaimEventDB.kind).where(ClaimEventDB.claim_number == OWN)))
    assert kinds == ["REPORTED", "DOCUMENTS_ADDED"]


async def test_el_mismo_envio_repetido_no_duplica_las_paginas(async_client, claims, storage):
    body = manifest([JPEG])

    first = await upload(async_client, OWN, body, [JPEG])
    again = await upload(async_client, OWN, body, [JPEG])

    assert (first.status_code, again.status_code) == (201, 200)
    assert again.json() == first.json()
    assert len(await documents_of(claims, OWN)) == 1


async def test_dos_envios_simultaneos_iguales_dejan_una_sola_copia(async_client, claims, storage):
    body = manifest([JPEG, PDF])

    responses = await asyncio.gather(upload(async_client, OWN, body, [JPEG, PDF]), upload(async_client, OWN, body, [JPEG, PDF]))

    assert sorted(r.status_code for r in responses) == [200, 201]
    assert len(await documents_of(claims, OWN)) == 2


async def test_el_evento_del_outbox_lleva_solo_identificadores(async_client, claims, storage):
    body = manifest([JPEG, PDF])

    await upload(async_client, OWN, body, [JPEG, PDF])

    event = await claims.scalar(select(OutboxMessageDB).where(OutboxMessageDB.event_type == "ClaimDocumentsAdded"))
    assert event.aggregate_id == OWN
    assert event.payload == {"claim_number": OWN, "upload_id": body["upload_id"], "pages": 2}


async def test_un_siniestro_ajeno_o_inexistente_responde_404_y_uno_decidido_409(async_client, claims, storage):
    foreign = await upload(async_client, FOREIGN, manifest([JPEG]), [JPEG])
    missing = await upload(async_client, "SIN-2026-99999", manifest([JPEG]), [JPEG])
    closed = await upload(async_client, CLOSED, manifest([JPEG]), [JPEG])

    assert (foreign.status_code, missing.status_code, closed.status_code) == (404, 404, 409)
    assert foreign.json() == missing.json()
    assert await count(claims, ClaimDocumentDB) == 0


async def test_el_envio_de_un_siniestro_no_se_reusa_en_otro_ni_por_otra_persona(async_client, claims, storage):
    await insured_auth_service.register(claims, user_id="ins-0002", email="otra.persona@example.com",
                                        full_name="Otra Persona", document_number=OTHER_DOCUMENT,
                                        phone="+573009998877", password=INSURED_PASSWORD)
    body = manifest([JPEG])
    await upload(async_client, OWN, body, [JPEG])

    other_owner = await upload(async_client, OWN, body, [JPEG], auth=insured_headers("ins-0002"))
    other_claim = await upload(async_client, PARTNER_CLAIM, body, [JPEG])

    assert other_owner.status_code == 404
    assert other_claim.status_code == 422
    assert len(await documents_of(claims, PARTNER_CLAIM)) == 0


@pytest.mark.parametrize("content, status", [
    (b"\x89PNG\r\n" + b"x" * 100, 422),
    (b"MZ ejecutable", 422),
    (JPEG + b"x" * 5_000_001, 413),
], ids=["png", "ejecutable", "mas-de-5-mb"])
async def test_paginas_que_no_son_jpeg_ni_pdf_o_muy_grandes_se_rechazan(async_client, claims, storage, content, status):
    response = await upload(async_client, OWN, manifest([content]), [content])

    assert response.status_code == status
    assert await count(claims, ClaimDocumentDB) == 0


async def test_mas_de_10_paginas_se_rechazan(async_client, claims, storage):
    contents = [JPEG] * 11

    by_manifest = await upload(async_client, OWN, manifest(contents), contents[:10])
    by_files = await upload(async_client, OWN, manifest(contents[:10]), contents)

    assert (by_manifest.status_code, by_files.status_code) == (422, 422)
    assert await count(claims, ClaimDocumentDB) == 0


async def test_una_pagina_que_no_coincide_con_su_huella_se_rechaza(async_client, claims, storage):
    body = manifest([JPEG, PDF])

    response = await upload(async_client, OWN, body, [JPEG, PDF + b"editado"])

    assert response.status_code == 422
    assert "cambió después de escanearla" in response.json()["detail"]
    assert await count(claims, ClaimDocumentDB) == 0


@pytest.mark.parametrize("body", [
    {"upload_id": "no-es-uuid", "pages": [{"position": 1, "sha256": "a" * 64}]},
    {"upload_id": str(uuid.uuid4()), "pages": []},
    {"upload_id": str(uuid.uuid4()), "pages": [{"position": 2, "sha256": "a" * 64}]},
    {"upload_id": str(uuid.uuid4()), "pages": [{"position": 1, "sha256": "a" * 64}], "extra": True},
], ids=["upload-id", "sin-paginas", "posiciones", "campo-extra"])
async def test_un_manifiesto_mal_formado_responde_422(async_client, claims, storage, body):
    response = await upload(async_client, OWN, body, [JPEG])

    assert response.status_code == 422


async def test_una_pagina_declarada_que_no_llega_se_rechaza(async_client, claims, storage):
    response = await upload(async_client, OWN, manifest([JPEG, JPEG]), [JPEG])

    assert response.status_code == 422
    assert await count(claims, ClaimDocumentDB) == 0


async def test_la_cuenta_en_verificacion_no_sube_documentos(async_client, claims, storage):
    await claims.execute(update(InsuredAccountDB).where(InsuredAccountDB.user_id == "ins-0001")
                         .values(verification_status="PENDING"))
    await claims.commit()

    response = await upload(async_client, OWN, manifest([JPEG]), [JPEG])

    assert response.status_code == 403
    assert await count(claims, ClaimDocumentDB) == 0


async def test_sin_sesion_o_con_otro_rol_no_se_suben_documentos(async_client, claims, storage):
    anonymous = await async_client.post(f"/api/v1/insured/claims/{OWN}/documents",
                                        data={"manifest": json.dumps(manifest([JPEG]))})
    analyst = await upload(async_client, OWN, manifest([JPEG]), [JPEG], auth=headers(ANALISTA))

    assert anonymous.status_code == 401
    assert analyst.status_code == 403


async def test_s3_caido_responde_503_y_el_mismo_envio_se_puede_reintentar(async_client, claims, storage, monkeypatch):
    body = manifest([JPEG])

    async def down(*args):
        raise StorageUnavailableError("sin S3")

    monkeypatch.setattr(storage, "put_bytes", down)
    failed = await upload(async_client, OWN, body, [JPEG])
    monkeypatch.undo()
    retried = await upload(async_client, OWN, body, [JPEG])

    assert failed.status_code == 503 and failed.headers["retry-after"] == "30"
    assert retried.status_code == 201


async def test_el_analista_ve_los_documentos_con_enlaces_firmados_y_el_de_otro_socio_no(async_client, claims, storage):
    await upload(async_client, OWN, manifest([JPEG, PDF]), [JPEG, PDF])
    await upload(async_client, PARTNER_CLAIM, manifest([PDF]), [PDF])

    own = (await async_client.get(f"/api/v1/experience/claims/{OWN}", headers=headers(ANALISTA))).json()
    partner = (await async_client.get(f"/api/v1/experience/claims/{PARTNER_CLAIM}", headers=headers(DEL_ALIADO))).json()
    denied = await async_client.get(f"/api/v1/experience/claims/{OWN}", headers=headers(DEL_ALIADO))

    assert [(d["position"], d["content_type"]) for d in own["documents"]] == [(1, "image/jpeg"), (2, "application/pdf")]
    assert all("X-Amz-Expires=900" in d["url"] and "X-Amz-Signature" in d["url"] for d in own["documents"])
    assert [d["content_type"] for d in partner["documents"]] == ["application/pdf"]
    assert denied.status_code == 404


async def test_sin_s3_el_expediente_lista_los_documentos_sin_enlace(async_client, claims, storage, monkeypatch):
    await upload(async_client, OWN, manifest([JPEG]), [JPEG])

    async def down(*args):
        raise StorageUnavailableError("sin S3")

    monkeypatch.setattr(storage, "view_url", down)
    detail = (await async_client.get(f"/api/v1/experience/claims/{OWN}", headers=headers(ANALISTA))).json()

    assert [d["url"] for d in detail["documents"]] == [None]


async def test_el_siniestro_tiene_un_tope_de_paginas(async_client, claims, storage, monkeypatch):
    monkeypatch.setattr("app.modules.claims.documents.MAX_PAGES_PER_CLAIM", 3)
    await upload(async_client, OWN, manifest([JPEG, JPEG]), [JPEG, JPEG])

    response = await upload(async_client, OWN, manifest([JPEG, JPEG]), [JPEG, JPEG])

    assert response.status_code == 422
    assert len(await documents_of(claims, OWN)) == 2


async def test_los_registros_no_llevan_el_documento_del_asegurado(async_client, claims, storage, caplog):
    caplog.set_level(logging.DEBUG)

    await upload(async_client, OWN, manifest([JPEG]), [JPEG])
    await upload(async_client, FOREIGN, manifest([JPEG]), [JPEG])

    assert MARIA_DOCUMENT not in caplog.text and OTHER_DOCUMENT not in caplog.text
