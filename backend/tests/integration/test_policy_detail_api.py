"""Detalle de póliza de operaciones contra PostgreSQL (HU-WEB-09: TC-01 a TC-07)."""
import json

import pytest
from httpx import AsyncClient

from tests.integration.policy_search_fixtures import (
    ALIADO,
    OTRO,
    REVOCADO,
    add_policies,
    claim,
    headers,
    payment,
    policy_row,
    seed_partners_and_users,
)

pytestmark = pytest.mark.integration

URL = "/api/v1/experience/policies/{}"
ANALISTA = "operaciones@solventa.example"
DEL_ALIADO = "analista@bancoaliado.example"
NOT_FOUND = {"detail": "No encontramos la póliza."}


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
        ],
    )
    postgres_session.add_all([
        payment("POL-2026-00001", 1, status="DECLINED", minutes=0, reason="Fondos insuficientes"),
        payment("POL-2026-00001", 2, minutes=5),
        claim("POL-2026-00001", 1, status="IN_REVIEW", days=1),
        claim("POL-2026-00001", 2, days=3),
    ])
    await postgres_session.commit()
    return postgres_session


async def detail(client, number, username=ANALISTA, role="analyst"):
    return await client.get(URL.format(number), headers=headers(username, role))


async def test_tc_01_el_detalle_trae_titular_plan_coberturas_primas_pagos_y_siniestros(async_client: AsyncClient, catalog):
    response = await detail(async_client, "POL-2026-00001")

    assert response.status_code == 200
    body = response.json()
    assert body["policy_number"] == "POL-2026-00001"
    assert body["status"] == "ACTIVE"
    assert body["plan"] == "premium"
    assert body["partner_name"] is None
    assert body["holder"]["name"] == "Carolina Prueba Gómez"
    assert body["holder"]["document_type"] == "CC"
    assert body["insured_amount"] == 200000000.0
    assert body["monthly_premium"] == 100000.0
    assert body["annual_premium"] == 1200000.0
    assert [c["name"] for c in body["coverages"]] == ["Muerte por cualquier causa", "Incapacidad total y permanente"]
    assert body["coverages"][0] == {"name": "Muerte por cualquier causa", "limit": 200000000.0, "deductible": 0.0}
    assert body["valid_from"] < body["valid_until"]
    assert len(body["payments"]) == 2
    assert [c["claim_number"] for c in body["claims"]] == ["SIN-2026-00002", "SIN-2026-00001"]
    assert set(body["claims"][0]) == {"claim_number", "status", "reported_at"}


async def test_tc_02_documento_correo_y_telefono_salen_enmascarados(async_client: AsyncClient, catalog):
    response = await detail(async_client, "POL-2026-00001")

    holder = response.json()["holder"]
    assert holder["document"] == "******4050"
    assert holder["email"] == "t*****@example.com"
    assert holder["phone"] == "******0000"
    raw = response.text
    for full in ("1020304050", "titular@example.com", "3000000000"):
        assert full not in raw
    assert "beneficiaries" not in raw


async def test_tc_03_una_poliza_inexistente_responde_404_sin_detalles_tecnicos(async_client: AsyncClient, catalog):
    response = await detail(async_client, "POL-2026-99999")

    assert response.status_code == 404
    assert response.json() == NOT_FOUND


@pytest.mark.parametrize("number", ["abc", "POL-2026-1' or '1'='1", "POL-2026-" + "1" * 30, "pol-2026-00001"])
async def test_tc_03_un_numero_con_otro_formato_responde_422(async_client: AsyncClient, catalog, number):
    response = await detail(async_client, number)

    assert response.status_code == 422


async def test_tc_04_poliza_b2b_sin_titular_sin_pagos_ni_siniestros(async_client: AsyncClient, catalog):
    response = await detail(async_client, "POL-2026-00002")

    body = response.json()
    assert body["partner_name"] == "Banco Aliado S.A."
    assert body["holder"] == {"name": None, "document_type": None, "document": "******0011", "email": None, "phone": None}
    assert body["payments"] == []
    assert body["claims"] == []


async def test_tc_05_el_usuario_de_un_socio_solo_ve_el_detalle_de_su_socio(async_client: AsyncClient, catalog):
    own = await detail(async_client, "POL-2026-00002", DEL_ALIADO)
    assert own.status_code == 200

    missing = await detail(async_client, "POL-2026-99999", DEL_ALIADO)
    for other in ("POL-2026-00003", "POL-2026-00001"):
        response = await detail(async_client, other, DEL_ALIADO)
        assert response.status_code == 404
        assert response.json() == missing.json() == NOT_FOUND


async def test_tc_05_el_usuario_de_un_socio_revocado_recibe_403(async_client: AsyncClient, catalog):
    response = await detail(async_client, "POL-2026-00004", "analista@revocado.example")

    assert response.status_code == 403


async def test_tc_06_solo_el_rol_analyst_ve_el_detalle(async_client: AsyncClient, catalog):
    advisor = await detail(async_client, "POL-2026-00001", "asesor@solventa.example", role="advisor")
    anonymous = await async_client.get(URL.format("POL-2026-00001"))
    ghost = await detail(async_client, "POL-2026-00001", "ya-no-existe@solventa.example")

    assert (advisor.status_code, anonymous.status_code, ghost.status_code) == (403, 401, 401)


async def test_tc_07_pagos_del_mas_reciente_al_mas_antiguo_sin_datos_del_medio(async_client: AsyncClient, catalog):
    response = await detail(async_client, "POL-2026-00001")

    payments = response.json()["payments"]
    assert [p["status"] for p in payments] == ["APPROVED", "DECLINED"]
    assert payments[0]["receipt_number"] == "REC-2026-00002"
    assert payments[0]["amount"] == 100000.0
    assert payments[0]["currency"] == "COP"
    assert payments[0]["method"] == "PSE"
    assert payments[1]["decline_reason"] == "Fondos insuficientes"
    assert payments[1]["receipt_number"] is None
    assert set(payments[0]) == {"paid_at", "amount", "currency", "method", "status", "receipt_number", "decline_reason"}
    assert "idempotency" not in json.dumps(payments)
    assert "tok_" not in response.text
