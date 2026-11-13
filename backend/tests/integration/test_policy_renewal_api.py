"""Renovación de la póliza desde el portal del analista (HU-MOV-11, AC-12 y SR-08)."""
from datetime import datetime, timedelta, timezone

import pytest
from httpx import AsyncClient
from sqlalchemy import select

from app.modules.policy.models import PolicyDB, PolicyEventDB
from tests.integration.policy_search_fixtures import ALIADO, add_policies, headers, policy_row, seed_partners_and_users

pytestmark = pytest.mark.integration

ANALISTA = "operaciones@solventa.example"
DEL_ALIADO = "analista@bancoaliado.example"
RENEWAL = "/api/v1/experience/policies/{}/renewal"


@pytest.fixture
async def catalog(postgres_session):
    await seed_partners_and_users(postgres_session)
    soon = policy_row("POL-2026-00011", "1020304050")
    soon[1]["valid_until"] = datetime.now(timezone.utc) + timedelta(days=10)
    expired = policy_row("POL-2026-00012", "7788990011", status="EXPIRED")
    expired[1]["valid_until"] = datetime.now(timezone.utc) - timedelta(days=3)
    other_partner = policy_row("POL-2026-00013", "4455667788", partner_id=ALIADO)
    other_partner[1]["valid_until"] = datetime.now(timezone.utc) + timedelta(days=10)
    await add_policies(postgres_session, [soon, expired, other_partner, policy_row("POL-2026-00014", "1122334455")])
    return postgres_session


async def test_el_analista_renueva_una_poliza_por_vencer_y_queda_en_la_auditoria(async_client: AsyncClient, catalog):
    response = await async_client.post(RENEWAL.format("POL-2026-00011"), headers=headers(ANALISTA))

    assert response.status_code == 200
    body = response.json()
    assert body["version"] == 2 and body["status"] == "ACTIVE"
    catalog.expire_all()
    event = await catalog.scalar(select(PolicyEventDB).where(PolicyEventDB.policy_number == "POL-2026-00011"))
    assert event.kind == "RENEWED" and event.actor == ANALISTA


async def test_una_poliza_vencida_renovada_queda_pendiente_de_pago(async_client: AsyncClient, catalog):
    response = await async_client.post(RENEWAL.format("POL-2026-00012"), headers=headers(ANALISTA))

    assert response.status_code == 200
    catalog.expire_all()
    assert (await catalog.get(PolicyDB, "POL-2026-00012")).status == "ISSUED"


async def test_fuera_de_la_ventana_de_30_dias_no_se_renueva(async_client: AsyncClient, catalog):
    response = await async_client.post(RENEWAL.format("POL-2026-00014"), headers=headers(ANALISTA))

    assert response.status_code == 409
    assert "30 días" in response.json()["detail"]


async def test_el_analista_de_un_socio_no_renueva_polizas_ajenas(async_client: AsyncClient, catalog):
    response = await async_client.post(RENEWAL.format("POL-2026-00011"), headers=headers(DEL_ALIADO))

    assert response.status_code == 404
