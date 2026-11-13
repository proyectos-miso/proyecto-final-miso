"""Pruebas de integración para TC-14: expiración automática de pólizas y renovación (RC-24, CTL-11)."""
from datetime import datetime, timedelta, timezone
import pytest
from httpx import AsyncClient
from sqlalchemy import select

from app.core.security import create_access_token
from app.modules.auth.service import auth_service
from app.modules.policy.models import PolicyDB, PolicyEventDB
from app.modules.policy.service import policy_service
from tests.integration.policy_search_fixtures import BENEFICIARIES
from tests.integration.otp_fixtures import signature_code


def advisor_headers() -> dict:
    return {"Authorization": f"Bearer {create_access_token(subject='asesor@solventa.com', role='advisor')}"}


def analyst_headers() -> dict:
    return {"Authorization": f"Bearer {create_access_token(subject='operaciones@solventa.example', role='analyst')}"}


@pytest.mark.integration
async def test_policy_expiration_and_renewal_flow(
    async_client: AsyncClient, postgres_session
):
    """TC-14: póliza vencida transiciona a EXPIRED y puede ser renovada extendiendo su vigencia."""
    await auth_service.seed_default_users(postgres_session)
    # 1. Crear cotización y emitir póliza
    quote_resp = await async_client.post(
        "/api/v1/experience/quotes",
        headers=advisor_headers(),
        json={
            "document_type": "CC",
            "document_number": "1020304050",
            "birth_date": "1990-05-15",
            "insured_amount": 100000000.0,
        },
    )
    assert quote_resp.status_code == 200
    quote_id = quote_resp.json()["quote_id"]

    issue_resp = await async_client.post(
        "/api/v1/experience/policies",
        headers=advisor_headers(),
        json={
            "quote_id": quote_id,
            "tier": "standard",
            "beneficiaries": BENEFICIARIES,
            "otp_code": await signature_code(async_client, advisor_headers()),
        },
    )
    assert issue_resp.status_code == 200
    policy_number = issue_resp.json()["policy_number"]

    # 2. Simular que la póliza ya venció modificando valid_until al pasado
    policy = await postgres_session.scalar(
        select(PolicyDB).where(PolicyDB.policy_number == policy_number)
    )
    past_date = datetime.now(timezone.utc) - timedelta(days=5)
    policy.valid_until = past_date
    policy.status = "ACTIVE"
    await postgres_session.commit()

    # 3. Ejecutar rutina periódica de expiración
    expired_list = await policy_service.expire_outdated_policies(postgres_session)
    assert policy_number in expired_list

    # 4. Verificar que el estado cambió a EXPIRED y se registró evento
    await postgres_session.refresh(policy)
    assert policy.status == "EXPIRED"

    event = await postgres_session.scalar(
        select(PolicyEventDB).where(
            PolicyEventDB.policy_number == policy_number,
            PolicyEventDB.kind == "EXPIRED",
        )
    )
    assert event is not None
    assert event.justification == "Vencimiento automático de la vigencia de la póliza"

    # 5. Invocar endpoint de renovación de póliza
    renew_resp = await async_client.post(
        f"/api/v1/experience/policies/{policy_number}/renewal",
        headers=analyst_headers(),
    )
    assert renew_resp.status_code == 200
    renew_data = renew_resp.json()
    assert renew_data["status"] == "ISSUED"

    # 6. Confirmar que la póliza vencida renovada queda emitida (pendiente de pago) y con vigencia extendida
    await postgres_session.refresh(policy)
    assert policy.status == "ISSUED"
    assert policy.valid_until > datetime.now(timezone.utc)

    # 7. Verificar evento de renovación en auditoría
    renew_event = await postgres_session.scalar(
        select(PolicyEventDB).where(
            PolicyEventDB.policy_number == policy_number,
            PolicyEventDB.kind == "RENEWED",
        )
    )
    assert renew_event is not None
