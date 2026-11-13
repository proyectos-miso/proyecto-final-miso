"""Pruebas de integración para TC-08: consentimiento previo obligatorio con OTP (RC-06, CTL-06)."""
import pytest
from httpx import AsyncClient
from sqlalchemy import select

from app.core.security import create_access_token
from app.modules.profile.models import ConsentDB


def advisor_headers() -> dict:
    return {"Authorization": f"Bearer {create_access_token(subject='asesor@solventa.com', role='advisor')}"}


@pytest.mark.integration
async def test_consent_flow_mandatory_otp_and_persistence_before_quote(
    async_client: AsyncClient, postgres_session
):
    """TC-08: cotización rechazada con HTTP 422 sin consentimiento; aceptada tras registro con OTP verificado."""
    doc_number = "52194832"

    # 1. Intentar cotizar sin consentimiento registrado
    unconsented_resp = await async_client.post(
        "/api/v1/experience/quotes",
        headers=advisor_headers(),
        json={
            "document_type": "CC",
            "document_number": doc_number,
            "birth_date": "1992-07-20",
            "insured_amount": 120000000.0,
        },
    )
    assert unconsented_resp.status_code == 422
    assert "El consentimiento informado del cliente es obligatorio" in unconsented_resp.json()["detail"]

    # 2. Solicitar OTP para el consentimiento
    otp_req_resp = await async_client.post(
        "/api/v1/experience/consent/otp-request",
        headers=advisor_headers(),
        json={
            "document_number": doc_number,
            "document_type": "CC",
            "phone": "+573109876543",
        },
    )
    assert otp_req_resp.status_code == 200
    assert otp_req_resp.json()["status"] == "OTP_SENT"

    # 3. Registrar el consentimiento con código canónico de prueba
    consent_reg_resp = await async_client.post(
        "/api/v1/experience/consent",
        headers=advisor_headers(),
        json={
            "document_number": doc_number,
            "channel": "web",
            "otp_code": "839201",
            "terms_accepted": True,
            "purpose": "Perfilamiento actuarial y cotización de crédito hipotecario",
            "scope": "Capacidad de pago y scoring",
            "validity_days": 30,
        },
    )
    assert consent_reg_resp.status_code == 200
    consent_data = consent_reg_resp.json()
    consent_id = consent_data["consent_id"]

    # Verificar persistencia en PostgreSQL
    stored_consent = await postgres_session.scalar(
        select(ConsentDB).where(ConsentDB.id == consent_id)
    )
    assert stored_consent is not None
    assert stored_consent.status == "GRANTED"
    assert stored_consent.document_number == doc_number

    # 4. Ahora cotizar con el consent_id registrado debe resultar exitoso
    quote_resp = await async_client.post(
        "/api/v1/experience/quotes",
        headers=advisor_headers(),
        json={
            "document_type": "CC",
            "document_number": doc_number,
            "birth_date": "1992-07-20",
            "insured_amount": 120000000.0,
            "consent_id": consent_id,
        },
    )
    assert quote_resp.status_code == 200
    assert len(quote_resp.json()["tiers"]) == 3
