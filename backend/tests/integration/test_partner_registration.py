"""Pruebas de integración para TC-13: registro de socios por autoservicio y promoción a producción (RC-23, CTL-10)."""
import pytest
from httpx import AsyncClient
from sqlalchemy import select

from app.core.security import create_access_token
from app.modules.auth.service import auth_service
from app.modules.rating.models import PartnerDB


def analyst_headers() -> dict:
    return {"Authorization": f"Bearer {create_access_token(subject='operaciones@solventa.example', role='analyst')}"}


@pytest.mark.integration
async def test_partner_self_registration_sandbox_and_production_approval(
    async_client: AsyncClient, postgres_session
):
    """TC-13: autoservicio crea credenciales en sandbox y operador aprueba promoción a producción."""
    await auth_service.seed_default_users(postgres_session)
    # 1. Registro por autoservicio en Quote Edge
    reg_payload = {
        "name": "Fintech Asegura Ya",
        "contact_email": "soporte@aseguraya.example.com",
    }
    reg_resp = await async_client.post(
        "/api/v1/quote-edge/partners/register",
        headers=analyst_headers(),
        json=reg_payload,
    )
    assert reg_resp.status_code == 201
    reg_data = reg_resp.json()
    partner_id = reg_data["partner_id"]
    client_id = reg_data["client_id"]
    client_secret = reg_data["client_secret"]

    assert reg_data["environment"] == "sandbox"
    assert reg_data["quota_limit"] == 1000
    assert reg_data["openapi_spec_url"] == "/openapi.json"

    # 2. Verificar persistencia en base de datos PostgreSQL
    partner = await postgres_session.scalar(
        select(PartnerDB).where(PartnerDB.partner_id == partner_id)
    )
    assert partner is not None
    assert partner.environment == "sandbox"
    assert partner.quota_limit == 1000
    assert partner.contact_email == "soporte@aseguraya.example.com"

    # 3. Intentar autenticación OAuth2 con las credenciales entregadas
    token_resp = await async_client.post(
        "/api/v1/quote-edge/auth/token",
        json={"client_id": client_id, "client_secret": client_secret},
    )
    assert token_resp.status_code == 200
    assert "access_token" in token_resp.json()

    # 4. Operador central de Solventa aprueba y promueve el socio a producción
    approve_resp = await async_client.post(
        f"/api/v1/experience/partners/{partner_id}/approve",
        headers=analyst_headers(),
        json={"quota_limit": 50000},
    )
    assert approve_resp.status_code == 200
    approve_data = approve_resp.json()
    assert approve_data["environment"] == "production"
    assert approve_data["quota_limit"] == 50000

    # 5. Confirmar actualización en base de datos
    await postgres_session.refresh(partner)
    assert partner.environment == "production"
    assert partner.quota_limit == 50000


@pytest.mark.integration
async def test_sin_sesion_o_desde_un_socio_no_se_registran_socios(async_client: AsyncClient, postgres_session):
    await auth_service.seed_default_users(postgres_session)
    payload = {"name": "Socio Sin Permiso", "contact_email": "contacto@sinpermiso.example.com"}
    from_partner = {"Authorization": f"Bearer {create_access_token(subject='analista@bancoaliado.example', role='analyst')}"}

    anonymous = await async_client.post("/api/v1/quote-edge/partners/register", json=payload)
    partner = await async_client.post("/api/v1/quote-edge/partners/register", headers=from_partner, json=payload)

    assert anonymous.status_code == 401
    assert partner.status_code == 403
    assert await postgres_session.scalar(select(PartnerDB).where(PartnerDB.name == "Socio Sin Permiso")) is None


@pytest.mark.integration
async def test_el_correo_del_socio_debe_ser_valido(async_client: AsyncClient, postgres_session):
    await auth_service.seed_default_users(postgres_session)

    response = await async_client.post(
        "/api/v1/quote-edge/partners/register", headers=analyst_headers(),
        json={"name": "Socio Correo", "contact_email": "no-es-un-correo"},
    )

    assert response.status_code == 422
