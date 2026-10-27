"""Búsqueda de pólizas de operaciones contra PostgreSQL (HU-WEB-08: TC-01 a TC-05 y TC-07)."""
import pytest
from httpx import AsyncClient
from sqlalchemy import select

from app.modules.auth.models import UserDB
from app.modules.auth.service import auth_service
from app.modules.policy.models import PolicyDB
from app.modules.rating.service import rating_service
from tests.integration.policy_search_fixtures import (
    ALIADO,
    OTRO,
    REVOCADO,
    add_policies,
    headers,
    policy_row,
    seed_partners_and_users,
)

pytestmark = pytest.mark.integration

URL = "/api/v1/experience/policies/search"
ANALISTA = "operaciones@solventa.example"
DEL_ALIADO = "analista@bancoaliado.example"
LIST_FIELDS = {"policy_number", "holder_name", "holder_document", "plan", "status", "valid_from", "valid_until", "partner_name"}


@pytest.fixture
async def catalog(postgres_session):
    await seed_partners_and_users(postgres_session)
    await add_policies(
        postgres_session,
        [
            policy_row("POL-2026-00001", "1020304050", holder="Carolina Prueba Gómez", plan="premium", age_days=6),
            policy_row("POL-2026-00002", "1020304050", status="ISSUED", holder="Carolina Prueba Gómez", age_days=5),
            policy_row("POL-2026-00003", "7788990011", partner_id=ALIADO, age_days=4),
            policy_row("POL-2026-00004", "5566778899", partner_id=OTRO, age_days=3),
            policy_row("POL-2026-00005", "4455667788", partner_id=REVOCADO, age_days=2),
            policy_row("POL-2026-00006", "1122334455", status="CANCELLED", holder="Andrés Ejemplo", age_days=1),
        ],
    )
    return postgres_session


async def search(client: AsyncClient, user: str = ANALISTA, **params):
    return await client.get(URL, params=params, headers=headers(user))


def numbers(response) -> list:
    return [item["policy_number"] for item in response.json()["items"]]


async def test_tc_01_por_documento_trae_sus_polizas_con_los_campos_de_la_lista(async_client: AsyncClient, catalog):
    response = await search(async_client, q="1020304050")

    assert response.status_code == 200
    assert numbers(response) == ["POL-2026-00002", "POL-2026-00001"]
    item = response.json()["items"][1]
    assert set(item) == LIST_FIELDS
    assert (item["holder_name"], item["holder_document"], item["plan"], item["status"], item["partner_name"]) == (
        "Carolina Prueba Gómez", "******4050", "premium", "ACTIVE", None
    )
    assert response.json()["has_more"] is False


async def test_tc_01_por_prefijo_de_numero_por_socio_y_por_titular(async_client: AsyncClient, catalog):
    assert numbers(await search(async_client, q="POL-2026-0000")) == [
        "POL-2026-00006", "POL-2026-00005", "POL-2026-00004", "POL-2026-00003", "POL-2026-00002", "POL-2026-00001"
    ]
    assert numbers(await search(async_client, q="banco aliado")) == ["POL-2026-00003"]
    assert numbers(await search(async_client, q="ANDRÉS")) == ["POL-2026-00006"]


async def test_tc_01_filtra_por_estado_y_la_poliza_b2b_no_trae_titular(async_client: AsyncClient, catalog):
    assert numbers(await search(async_client, status="CANCELLED")) == ["POL-2026-00006"]
    b2b = (await search(async_client, q="7788990011")).json()["items"][0]
    assert (b2b["holder_name"], b2b["holder_document"], b2b["partner_name"]) == (None, "******0011", "Banco Aliado S.A.")


async def test_tc_02_al_emitir_desde_el_portal_se_guarda_el_titular(async_client: AsyncClient, postgres_session):
    advisor = headers("asesor@solventa.com", role="advisor")
    quote = (
        await async_client.post(
            "/api/v1/experience/quotes",
            headers=advisor,
            json={"document_type": "CC", "document_number": "1020304050", "birth_date": "1990-05-15", "insured_amount": 1e8},
        )
    ).json()
    holder = {"name": "Carolina Prueba Gómez", "document_type": "CC", "email": "carolina@example.com", "phone": "3001234567"}

    response = await async_client.post(
        "/api/v1/experience/policies", headers=advisor, json={"quote_id": quote["quote_id"], "tier": "standard", "holder": holder}
    )

    assert response.status_code == 200
    stored = await postgres_session.scalar(select(PolicyDB).where(PolicyDB.quote_id == quote["quote_id"]))
    assert (stored.holder_name, stored.holder_document_type, stored.holder_email, stored.holder_phone) == (
        "Carolina Prueba Gómez", "CC", "carolina@example.com", "3001234567"
    )
    assert "carolina@example.com" not in response.text


async def test_tc_03_el_usuario_de_un_socio_solo_ve_las_de_su_socio(async_client: AsyncClient, catalog):
    assert numbers(await search(async_client, user=DEL_ALIADO)) == ["POL-2026-00003"]
    for other in ["5566778899", "POL-2026-00004", "1020304050", "Otra Aseguradora", "Carolina"]:
        assert numbers(await search(async_client, user=DEL_ALIADO, q=other)) == [], other


async def test_tc_03_el_usuario_de_un_socio_revocado_recibe_403(async_client: AsyncClient, catalog):
    response = await search(async_client, user="analista@revocado.example")

    assert response.status_code == 403


async def test_tc_04_solo_el_rol_analyst_busca(async_client: AsyncClient, catalog):
    assert (await async_client.get(URL, headers=headers("asesor@solventa.com", role="advisor"))).status_code == 403
    assert (await async_client.get(URL)).status_code == 401
    assert (await async_client.get(URL, headers=headers("nadie@solventa.example"))).status_code == 401


async def test_tc_05_maximo_50_resultados_los_mas_recientes_y_sin_contacto(async_client: AsyncClient, postgres_session):
    await seed_partners_and_users(postgres_session)
    await add_policies(
        postgres_session,
        [policy_row(f"POL-2026-1{i:04d}", "3003003000", holder="Titular Repetido", age_days=i) for i in range(60)],
    )

    body = (await search(async_client, q="3003003000")).json()

    assert len(body["items"]) == 50 and body["has_more"] is True
    assert body["items"][0]["policy_number"] == "POL-2026-10000"
    assert all(set(item) == LIST_FIELDS for item in body["items"])
    assert "3003003000" not in str(body) and "titular@example.com" not in str(body)


@pytest.mark.parametrize("text", ["%", "_", "' or 1=1 --", "\\"])
async def test_tc_05_comodines_y_comillas_se_buscan_literales(async_client: AsyncClient, catalog, text):
    response = await search(async_client, q=text)

    assert response.status_code == 200
    assert numbers(response) == []


async def test_tc_05_texto_de_mas_de_80_caracteres_o_estado_invalido_responde_422(async_client: AsyncClient, catalog):
    assert (await search(async_client, q="x" * 81)).status_code == 422
    assert (await search(async_client, status="PAID")).status_code == 422


async def test_tc_07_los_analistas_de_prueba_quedan_con_su_rol_y_su_socio(postgres_session):
    await rating_service.seed_default_partners(postgres_session)
    await auth_service.seed_default_users(postgres_session)

    users = {u.username: u for u in (await postgres_session.scalars(select(UserDB))).all()}
    assert users["operaciones@solventa.example"].role == "analyst"
    assert users["operaciones@solventa.example"].partner_id is None
    assert users["analista@bancoaliado.example"].role == "analyst"
    assert users["analista@bancoaliado.example"].partner_id == ALIADO
    assert users["asesor@solventa.com"].role == "advisor"
