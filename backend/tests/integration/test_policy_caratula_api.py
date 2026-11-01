"""Carátula de la póliza contra PostgreSQL y S3 emulado con Floci (HU-WEB-16: TC-01 a TC-08, TC-10 y TC-12)."""
import asyncio
import os
import time
import uuid
from datetime import datetime, timedelta, timezone

import httpx
import pytest
from httpx import AsyncClient
from sqlalchemy import select

from app.core.storage import S3DocumentStorage, get_document_storage
from app.main import app
from app.modules.policy import document as policy_document
from app.modules.policy.models import PolicyDB
from tests.integration.policy_search_fixtures import (
    ALIADO,
    OTRO,
    REVOCADO,
    add_policies,
    headers,
    policy_row,
    seed_partners_and_users,
)
from tests.pdf_text import pdf_text

pytestmark = pytest.mark.integration

URL = "/api/v1/experience/policies/{}/caratula"
FLOCI = os.environ.get("FLOCI_ENDPOINT_URL", "http://localhost:4566")
ANALISTA = "operaciones@solventa.example"
DEL_ALIADO = "analista@bancoaliado.example"
NOT_FOUND = {"detail": "No encontramos la póliza."}
EN_GENERACION = {"detail": "La carátula está en generación. Intente de nuevo en unos segundos."}


class CountingStorage:
    """Floci de verdad, contando cuántas veces se guarda un PDF."""

    def __init__(self, inner):
        self.inner = inner
        self.puts = 0

    async def put_pdf(self, key, data):
        self.puts += 1
        await self.inner.put_pdf(key, data)

    async def download_url(self, key, filename, expires_in):
        return await self.inner.download_url(key, filename, expires_in)


@pytest.fixture
async def floci():
    """Con el emulador el adaptador usa credenciales de prueba: no hacen falta las de AWS."""
    s3 = S3DocumentStorage(f"caratulas-prueba-{uuid.uuid4().hex[:8]}", "us-east-1", FLOCI)
    await s3.ensure_bucket()
    storage = CountingStorage(s3)
    app.dependency_overrides[get_document_storage] = lambda: storage
    yield storage
    app.dependency_overrides.pop(get_document_storage, None)


@pytest.fixture
async def catalog(postgres_session):
    await seed_partners_and_users(postgres_session)
    await add_policies(
        postgres_session,
        [
            policy_row("POL-2026-00001", "1020304050", holder="Carolina Prueba Gómez", plan="premium", age_days=6),
            policy_row("POL-2026-00002", "7788990011", partner_id=ALIADO, age_days=4),
            policy_row("POL-2026-00003", "5566778899", partner_id=OTRO, age_days=3),
            policy_row("POL-2026-00004", "4455667788", partner_id=REVOCADO, age_days=2),
            policy_row("POL-2026-00005", "3344556677", status="CANCELLED", age_days=1),
        ],
    )
    return postgres_session


async def caratula(client, number, username=ANALISTA, role="analyst"):
    return await client.get(URL.format(number), headers=headers(username, role))


def download(url: str) -> httpx.Response:
    return httpx.get(url, timeout=5)


async def stored_key(session, number):
    session.expire_all()
    return await session.scalar(select(PolicyDB.pdf_s3_key).where(PolicyDB.policy_number == number))


async def test_tc_01_el_analista_recibe_un_enlace_de_15_minutos_que_descarga_el_pdf(async_client: AsyncClient, catalog, floci):
    before = datetime.now(timezone.utc)
    response = await caratula(async_client, "POL-2026-00001")

    assert response.status_code == 200
    assert response.headers["cache-control"] == "no-store"
    body = response.json()
    assert set(body) == {"url", "expires_at"}
    expires_at = datetime.fromisoformat(body["expires_at"])
    assert timedelta(minutes=14, seconds=59) <= expires_at - before <= timedelta(minutes=15, seconds=5)
    assert "X-Amz-Expires=900" in body["url"]

    pdf = download(body["url"])
    assert pdf.status_code == 200
    assert pdf.content.startswith(b"%PDF")
    assert pdf.headers["content-type"] == "application/pdf"
    assert pdf.headers["content-disposition"] == 'attachment; filename="caratula-POL-2026-00001.pdf"'
    assert await stored_key(catalog, "POL-2026-00001") == "caratulas/POL-2026-00001.pdf"


async def test_tc_02_el_pdf_descargado_trae_los_datos_con_el_documento_enmascarado(async_client: AsyncClient, catalog, floci):
    text = pdf_text(download((await caratula(async_client, "POL-2026-00001")).json()["url"]).content)

    for expected in [
        "Carátula de la póliza POL-2026-00001",
        "Nombre: Carolina Prueba Gómez",
        "Documento: CC ******4050",
        "Plan: Premium",
        "Muerte por cualquier causa: $ 200.000.000 COP",
        "Prima mensual: $ 100.000 COP",
        "Prima anual: $ 1.200.000 COP",
        "Vigencia: ",
    ]:
        assert expected in text, expected
    for hidden in ["1020304050", "titular@example.com", "3000000000", "Enfermedades graves"]:
        assert hidden not in text


async def test_tc_02_una_poliza_b2b_sin_titular_muestra_sin_nombre_y_el_socio(async_client: AsyncClient, catalog, floci):
    response = await caratula(async_client, "POL-2026-00002", username=DEL_ALIADO)

    text = pdf_text(download(response.json()["url"]).content)
    assert "Nombre: Sin nombre registrado" in text
    assert "Documento: ******0011" in text
    assert "Socio: Banco Aliado S.A." in text


async def test_tc_03_un_enlace_vencido_se_rechaza_y_al_pedir_otro_descarga(async_client: AsyncClient, catalog, floci, monkeypatch):
    monkeypatch.setattr(policy_document, "LINK_TTL", timedelta(seconds=1))
    expired = (await caratula(async_client, "POL-2026-00001")).json()["url"]
    await asyncio.sleep(2.5)

    rejected = download(expired)
    assert rejected.status_code == 403
    assert b"expired" in rejected.content

    monkeypatch.setattr(policy_document, "LINK_TTL", timedelta(minutes=15))
    fresh = (await caratula(async_client, "POL-2026-00001")).json()["url"]
    assert fresh != expired
    assert download(fresh).status_code == 200


async def test_tc_04_el_usuario_de_un_socio_solo_descarga_las_de_su_socio(async_client: AsyncClient, catalog, floci):
    assert (await caratula(async_client, "POL-2026-00002", username=DEL_ALIADO)).status_code == 200

    for number in ["POL-2026-00003", "POL-2026-00001", "POL-2026-09999"]:
        response = await caratula(async_client, number, username=DEL_ALIADO)
        assert (response.status_code, response.json()) == (404, NOT_FOUND), number
    assert floci.puts == 1
    assert await stored_key(catalog, "POL-2026-00003") is None


async def test_tc_05_solo_el_rol_analyst_con_su_socio_habilitado_pide_la_caratula(async_client: AsyncClient, catalog, floci):
    advisor = await caratula(async_client, "POL-2026-00001", username="asesor@solventa.example", role="advisor")
    anonymous = await async_client.get(URL.format("POL-2026-00001"))
    revoked = await caratula(async_client, "POL-2026-00004", username="analista@revocado.example")

    assert (advisor.status_code, anonymous.status_code, revoked.status_code) == (403, 401, 403)
    assert floci.puts == 0


@pytest.mark.parametrize("number", ["POL-2026-1", "POL-2026-00001.pdf", "..%2Fcaratulas%2FPOL-2026-00001", "pol-2026-00001"])
async def test_tc_06_un_numero_fuera_del_patron_no_llega_a_s3(async_client: AsyncClient, catalog, floci, number):
    response = await caratula(async_client, number)

    assert response.status_code in (404, 422)
    assert floci.puts == 0


async def test_tc_07_el_pdf_se_genera_una_vez_y_luego_solo_se_firma(async_client: AsyncClient, catalog, floci):
    first = await caratula(async_client, "POL-2026-00001")
    second = await caratula(async_client, "POL-2026-00001")

    assert (first.status_code, second.status_code) == (200, 200)
    assert floci.puts == 1
    assert download(second.json()["url"]).status_code == 200


async def test_tc_08_con_s3_caido_informa_que_esta_en_generacion_y_luego_la_entrega(async_client: AsyncClient, catalog, floci):
    caido = S3DocumentStorage(floci.inner.bucket, "us-east-1", "http://127.0.0.1:9")
    app.dependency_overrides[get_document_storage] = lambda: caido

    response = await caratula(async_client, "POL-2026-00001")

    assert (response.status_code, response.json()) == (503, EN_GENERACION)
    assert response.headers["retry-after"] == "5"
    assert await stored_key(catalog, "POL-2026-00001") is None

    app.dependency_overrides[get_document_storage] = lambda: floci
    recovered = await caratula(async_client, "POL-2026-00001")
    assert recovered.status_code == 200
    assert download(recovered.json()["url"]).status_code == 200


async def test_tc_10_una_poliza_cancelada_no_tiene_caratula(async_client: AsyncClient, catalog, floci):
    response = await caratula(async_client, "POL-2026-00005")

    assert (response.status_code, response.json()) == (409, {"detail": "La póliza está cancelada y no tiene carátula vigente."})
    assert floci.puts == 0


async def test_tc_12_el_p95_del_enlace_queda_por_debajo_de_150_ms_y_la_primera_generacion_de_1_s(
    async_client: AsyncClient, catalog, floci
):
    start = time.perf_counter()
    assert (await caratula(async_client, "POL-2026-00001")).status_code == 200
    first = time.perf_counter() - start

    durations = []
    for _ in range(50):
        start = time.perf_counter()
        assert (await caratula(async_client, "POL-2026-00001")).status_code == 200
        durations.append(time.perf_counter() - start)
    p95 = sorted(durations)[int(len(durations) * 0.95) - 1]

    print(f"\nHU-WEB-16 primera generación {first * 1000:.0f} ms; p95 de 50 enlaces {p95 * 1000:.1f} ms")
    assert first < 1.0
    assert p95 < 0.150


async def test_tc_16_con_el_emulador_el_bucket_se_crea_una_vez_y_el_enlace_usa_la_direccion_publica():
    bucket = f"caratulas-prueba-{uuid.uuid4().hex[:8]}"
    storage = S3DocumentStorage(bucket, "us-east-1", FLOCI, public_endpoint_url="http://127.0.0.1:4566")

    await storage.ensure_bucket()
    await storage.ensure_bucket()
    await storage.put_pdf("caratulas/POL-2026-00001.pdf", b"%PDF-1.4 prueba")

    url = await storage.download_url("caratulas/POL-2026-00001.pdf", "caratula-POL-2026-00001.pdf", 900)
    assert url.startswith(f"http://127.0.0.1:4566/{bucket}/caratulas/POL-2026-00001.pdf?")
    assert download(url).content == b"%PDF-1.4 prueba"
