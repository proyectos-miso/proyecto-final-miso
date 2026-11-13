"""Pruebas de integración para TC-12: adjudicación de cobros al partner_id autenticado del token (RC-10, CTL-09)."""
import pytest
from httpx import AsyncClient
from sqlalchemy import select

from app.core.security import create_access_token
from app.modules.payments.models import PaymentTransactionDB
from tests.integration.policy_search_fixtures import BENEFICIARIES


def partner_advisor_headers(partner_id: str = "bancolombia", **extra) -> dict:
    token = create_access_token(
        subject="asesor_aliado@bancolombia.example.com",
        role="advisor",
        claims={"partner_id": partner_id, "advisor_name": "Asesor Banco Aliado"},
    )
    return {"Authorization": f"Bearer {token}", **extra}


@pytest.mark.integration
async def test_charge_premium_assigns_partner_id_from_authenticated_token(
    async_client: AsyncClient, postgres_session
):
    """TC-12: el cobro de prima adjudica la transacción al partner_id de los claims del token JWT."""
    # 1. Crear cotización y emitir póliza
    headers = partner_advisor_headers("bancolombia")
    quote_resp = await async_client.post(
        "/api/v1/experience/quotes",
        headers=headers,
        json={
            "document_type": "CC",
            "document_number": "1020304050",
            "birth_date": "1990-05-15",
            "insured_amount": 100000000.0,
        },
    )
    assert quote_resp.status_code == 200
    quote_data = quote_resp.json()
    quote_id = quote_data["quote_id"]

    issue_resp = await async_client.post(
        "/api/v1/experience/policies",
        headers=headers,
        json={
            "quote_id": quote_id,
            "tier": "standard",
            "beneficiaries": BENEFICIARIES,
            "otp_code": "839201",
        },
    )
    assert issue_resp.status_code == 200
    policy = issue_resp.json()
    policy_number = policy["policy_number"]

    # 2. Realizar cobro autenticado con asesor de bancolombia
    charge_headers = partner_advisor_headers("bancolombia", **{"Idempotency-Key": "idemp-tc12-partner-01"})
    charge_resp = await async_client.post(
        "/api/v1/experience/payments/charge",
        headers=charge_headers,
        json={
            "policy_number": policy_number,
            "amount": policy["conditions"]["monthly_premium"],
            "payment_method": "PSE",
            "token_or_account": "tok_pse_bancolombia",
        },
    )
    assert charge_resp.status_code == 200
    receipt = charge_resp.json()
    assert receipt["status"] == "APPROVED"

    # 3. Verificar en PostgreSQL que partner_id es 'bancolombia' y no 'default'
    tx = await postgres_session.scalar(
        select(PaymentTransactionDB).where(PaymentTransactionDB.policy_number == policy_number)
    )
    assert tx is not None
    assert tx.partner_id == "bancolombia"
    assert tx.partner_id != "default"
