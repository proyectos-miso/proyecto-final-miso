"""Cotización con rechazo de suscripción contra PostgreSQL (HU-WEB-07: TC-02 a TC-05)."""
import pytest
from httpx import AsyncClient
from sqlalchemy import select, text
from sqlalchemy.exc import DBAPIError

from app.core.security import create_access_token
from app.modules.policy.models import PolicyDB
from app.modules.rating.models import QuoteDB
from app.modules.underwriting.models import UnderwritingRejectionDB

pytestmark = pytest.mark.integration

DOCUMENT = "1020304050"


def advisor_headers() -> dict:
    return {"Authorization": f"Bearer {create_access_token(subject='asesor@solventa.com', role='advisor')}"}


async def quote(client: AsyncClient, amount: float, birth_date: str = "1990-05-15", headers=None):
    body = {"document_type": "CC", "document_number": DOCUMENT, "birth_date": birth_date, "insured_amount": amount}
    return await client.post("/api/v1/experience/quotes", headers=advisor_headers() if headers is None else headers, json=body)


async def test_tc_02_dentro_del_cupo_trae_planes_sin_decision(async_client: AsyncClient, postgres_session):
    response = await quote(async_client, 200_000_000)

    body = response.json()
    assert response.status_code == 200
    assert body["status"] == "active"
    assert len(body["tiers"]) == 3
    assert body["underwriting"] is None


async def test_tc_02_por_encima_del_cupo_requiere_revision_con_motivo_y_suma_maxima(async_client: AsyncClient, postgres_session):
    response = await quote(async_client, 900_000_000)

    body = response.json()
    assert response.status_code == 200
    assert body["status"] == "review_required"
    assert body["tiers"] == []
    assert body["quote_id"].startswith("UWR-")
    assert body["underwriting"]["max_approvable_amount"] == 700_000_000
    assert "cupo de aprobación directa" in body["underwriting"]["reason"]


async def test_tc_02_sin_sesion_responde_401(async_client: AsyncClient):
    response = await quote(async_client, 900_000_000, headers={})

    assert response.status_code == 401


async def test_tc_03_cada_rechazo_queda_registrado_sin_el_puntaje(async_client: AsyncClient, postgres_session):
    await quote(async_client, 900_000_000)
    await quote(async_client, 500_000_000, birth_date="1955-01-10")

    rows = (await postgres_session.scalars(select(UnderwritingRejectionDB).order_by(UnderwritingRejectionDB.created_at))).all()
    assert [(r.rule_id, r.requested_amount, r.max_approvable_amount) for r in rows] == [
        ("UW-01", 900_000_000, 700_000_000),
        ("UW-02", 500_000_000, 300_000_000),
    ]
    assert all(r.document_number == DOCUMENT and r.reason and r.created_at for r in rows)
    assert "score" not in " ".join(UnderwritingRejectionDB.__table__.columns.keys())
    assert await postgres_session.scalar(select(QuoteDB).limit(1)) is None


async def test_tc_04_el_identificador_de_un_rechazo_no_se_puede_emitir(async_client: AsyncClient, postgres_session):
    rejected = (await quote(async_client, 900_000_000)).json()

    response = await async_client.post(
        "/api/v1/experience/policies", headers=advisor_headers(), json={"quote_id": rejected["quote_id"], "tier": "standard"}
    )

    assert response.status_code == 404
    assert await postgres_session.scalar(select(PolicyDB).limit(1)) is None


@pytest.mark.parametrize(
    "statement",
    ["UPDATE underwriting_rejections SET max_approvable_amount = 900000000", "DELETE FROM underwriting_rejections"],
    ids=["update", "delete"],
)
async def test_tc_05_la_base_impide_cambiar_o_borrar_un_rechazo(async_client: AsyncClient, postgres_session, statement):
    await quote(async_client, 900_000_000)

    with pytest.raises(DBAPIError, match="no se pueden modificar ni borrar"):
        await postgres_session.execute(text(statement))
    await postgres_session.rollback()

    row = await postgres_session.scalar(select(UnderwritingRejectionDB))
    assert row.max_approvable_amount == 700_000_000
