"""Hotfix RC-32: solo un token de acceso explícito abre la API y un código TOTP no sirve dos veces (TC-01 a TC-04)."""
import time
from datetime import datetime, timedelta, timezone

import jwt
import pytest
from httpx import AsyncClient

from app.core.config import settings
from app.core.security import create_access_token, create_partner_access_token, create_refresh_token, generate_totp
from app.modules.auth.service import DEFAULT_SEED_PASSWORD, DEFAULT_SEED_TOTP_SECRET, DEFAULT_SEED_USERNAME

pytestmark = pytest.mark.integration

LOGIN = "/api/v1/experience/auth/login"
ADVISOR_ENDPOINT = "/api/v1/experience/catalogs/cities"
QUOTES = "/api/v1/experience/quotes"
PARTNER_QUOTE = "/api/v1/quote-edge/quotes/Q-NO-EXISTE"


def token_without_type(**claims) -> str:
    now = datetime.now(timezone.utc)
    payload = {"sub": "asesor@solventa.example", "role": "advisor", "iat": now, "exp": now + timedelta(minutes=5)}
    payload.update(claims)
    return jwt.encode(payload, settings.JWT_SECRET_KEY.get_secret_value(), algorithm=settings.JWT_ALGORITHM)


def bearer(token: str) -> dict:
    return {"Authorization": f"Bearer {token}"}


async def test_tc_01_un_token_sin_tipo_no_abre_la_api(async_client: AsyncClient):
    response = await async_client.post(QUOTES, json={}, headers=bearer(token_without_type()))

    assert response.status_code == 401
    assert response.json()["detail"] == "Token no permitido como token de acceso"


async def test_tc_01_un_token_de_refresco_no_abre_la_api(async_client: AsyncClient):
    refresh = create_refresh_token(subject="asesor@solventa.example", role="advisor")

    response = await async_client.post(QUOTES, json={}, headers=bearer(refresh))

    assert response.status_code == 401


async def test_tc_01_el_token_de_acceso_sigue_funcionando(async_client: AsyncClient):
    access = create_access_token(subject="asesor@solventa.example", role="advisor")

    response = await async_client.post(QUOTES, json={}, headers=bearer(access))

    assert response.status_code == 422


async def test_tc_02_un_token_de_socio_sin_tipo_no_abre_el_canal_b2b(async_client: AsyncClient):
    forged = token_without_type(sub="banco_aliado", role="partner", partner_id="banco-aliado-1")
    valid = create_partner_access_token(client_id="banco_aliado", partner_id="banco-aliado-1")

    rejected = await async_client.get(PARTNER_QUOTE, headers=bearer(forged))
    accepted = await async_client.get(PARTNER_QUOTE, headers=bearer(valid))

    assert rejected.status_code == 401
    assert accepted.status_code != 401


def login_body(code: str) -> dict:
    return {"username": DEFAULT_SEED_USERNAME, "password": DEFAULT_SEED_PASSWORD, "totp_code": code}


async def test_tc_03_el_mismo_codigo_totp_no_sirve_dos_veces(async_client: AsyncClient):
    code = generate_totp(DEFAULT_SEED_TOTP_SECRET)

    first = await async_client.post(LOGIN, json=login_body(code))
    second = await async_client.post(LOGIN, json=login_body(code))

    assert first.status_code == 200
    assert second.status_code == 401
    assert second.json()["detail"] == "Credenciales o código de verificación inválidos"


async def test_tc_04_un_codigo_anterior_al_ultimo_usado_tampoco_sirve(async_client: AsyncClient, monkeypatch):
    now = int(time.time())
    previous = generate_totp(DEFAULT_SEED_TOTP_SECRET, timestamp=now - 30)
    current = generate_totp(DEFAULT_SEED_TOTP_SECRET, timestamp=now)
    following = generate_totp(DEFAULT_SEED_TOTP_SECRET, timestamp=now + 30)
    if len({previous, current, following}) < 3:
        pytest.skip("Coincidencia improbable de códigos en pasos seguidos")

    assert (await async_client.post(LOGIN, json=login_body(current))).status_code == 200
    assert (await async_client.post(LOGIN, json=login_body(previous))).status_code == 401
    monkeypatch.setattr(time, "time", lambda: now + 30)
    assert (await async_client.post(LOGIN, json=login_body(following))).status_code == 200


async def test_tc_04_el_endpoint_de_catalogos_no_pide_token(async_client: AsyncClient):
    response = await async_client.get(ADVISOR_ENDPOINT)

    assert response.status_code == 200
