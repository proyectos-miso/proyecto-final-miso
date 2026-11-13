"""Pruebas de integración para TC-09: firma digital de póliza con OTP, inmutabilidad y hash (RC-02, CTL-07)."""
import pytest
from httpx import AsyncClient
from sqlalchemy import select, text
from sqlalchemy.exc import DBAPIError

from app.core.security import create_access_token
from app.modules.policy.models import PolicySignatureDB
from tests.integration.policy_search_fixtures import BENEFICIARIES


def advisor_headers() -> dict:
    return {"Authorization": f"Bearer {create_access_token(subject='asesor@solventa.com', role='advisor')}"}


@pytest.mark.integration
async def test_policy_signature_requires_otp_and_creates_immutable_record(
    async_client: AsyncClient, postgres_session
):
    """TC-09: emisión con código OTP registra fila en policy_signatures y bloquea alteraciones por trigger."""
    # 1. Crear cotización con el titular por defecto (consentimiento precargado en seed)
    quote_resp = await async_client.post(
        "/api/v1/experience/quotes",
        headers=advisor_headers(),
        json={
            "document_type": "CC",
            "document_number": "1020304050",
            "birth_date": "1990-05-15",
            "insured_amount": 100000000.0,
            "financial_entity": "Davivienda",
            "term_months": 240,
        },
    )
    assert quote_resp.status_code == 200
    quote_data = quote_resp.json()
    quote_id = quote_data["quote_id"]

    # 2. Emisión con código OTP válido (código canónico de prueba)
    issue_payload = {
        "quote_id": quote_id,
        "tier": "standard",
        "beneficiaries": BENEFICIARIES,
        "otp_code": "839201",
        "holder": {
            "name": "María Ruiz",
            "document_type": "CC",
            "email": "maria.ruiz@correo.co",
            "phone": "+573001112233",
        },
    }
    issue_resp = await async_client.post(
        "/api/v1/experience/policies",
        headers=advisor_headers(),
        json=issue_payload,
    )
    assert issue_resp.status_code == 200
    policy_data = issue_resp.json()
    policy_number = policy_data["policy_number"]

    # 3. Verificar que la firma quedó guardada en PostgreSQL
    sig = await postgres_session.scalar(
        select(PolicySignatureDB).where(PolicySignatureDB.policy_number == policy_number)
    )
    assert sig is not None
    assert sig.policy_number == policy_number
    assert len(sig.pdf_hash_sha256) == 64
    assert len(sig.otp_code_hash) == 64
    assert sig.document_number == "1020304050"

    # 4. Probar inmutabilidad: UPDATE debe fallar por el trigger de PostgreSQL
    with pytest.raises((DBAPIError, Exception)):
        await postgres_session.execute(
            text(f"UPDATE policy_signatures SET pdf_hash_sha256 = 'alterado' WHERE policy_number = '{policy_number}'")
        )
        await postgres_session.commit()

    # 5. Probar inmutabilidad: DELETE debe fallar por el trigger de PostgreSQL
    with pytest.raises((DBAPIError, Exception)):
        await postgres_session.execute(
            text(f"DELETE FROM policy_signatures WHERE policy_number = '{policy_number}'")
        )
        await postgres_session.commit()


@pytest.mark.integration
async def test_policy_signature_fails_with_invalid_otp(
    async_client: AsyncClient, postgres_session
):
    """Rechazo con HTTP 422 cuando el código OTP de firma es incorrecto o inválido."""
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
            "otp_code": "000000",
        },
    )
    assert issue_resp.status_code == 422
    assert "Código OTP de firma electrónica inválido o expirado" in issue_resp.json()["detail"]
