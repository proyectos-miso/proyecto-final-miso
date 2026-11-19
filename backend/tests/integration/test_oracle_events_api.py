"""Siniestros paramétricos desde eventos firmados del oráculo (HU-MOV-10, RC-26)."""
import asyncio
import json
import time

import pytest
from pydantic import SecretStr
from sqlalchemy import func, select

from app.core.config import settings
from app.modules.claims.models import ClaimDB
from app.modules.claims.oracle import signature
from tests.integration.policy_search_fixtures import add_policies, policy_row

pytestmark = pytest.mark.integration

URL = "/api/v1/oracle/events"
SECRET = "llave-del-oraculo-de-pruebas-0123456789"


@pytest.fixture(autouse=True)
def oracle_secret(monkeypatch):
    monkeypatch.setattr(settings, "ORACLE_SECRET", SecretStr(SECRET))


def row(number, document, city, status="ACTIVE"):
    quote, policy = policy_row(number, document, status=status)
    quote["city"] = city
    return quote, policy


@pytest.fixture
async def policies(postgres_session):
    await add_policies(postgres_session, [
        row("POL-2026-13001", "1111111111", "Bogotá"),
        row("POL-2026-13002", "2222222222", "  bogota "),
        row("POL-2026-13003", "3333333333", "Medellín"),
        row("POL-2026-13004", "4444444444", "Bogotá", status="CANCELLED"),
        row("POL-2026-13005", "5555555555", None),
    ])


def event(event_id="sismo-2026-11-14-001", magnitude=7.1, city="Bogotá") -> bytes:
    return json.dumps({"event_id": event_id, "type": "SISMO", "magnitude": magnitude, "city": city,
                       "occurred_at": "2026-11-14T15:00:00Z"}).encode()


def signed(body: bytes, secret: str = SECRET, timestamp: int = None) -> dict:
    stamp = str(timestamp if timestamp is not None else int(time.time()))
    return {"X-Oracle-Timestamp": stamp, "X-Oracle-Signature": signature(secret, stamp, body),
            "Content-Type": "application/json"}


async def post(client, body: bytes, headers=None):
    return await client.post(URL, content=body, headers=headers if headers is not None else signed(body))


async def claims(session) -> list:
    return list(await session.scalars(select(ClaimDB).where(ClaimDB.origin == "PARAMETRIC").order_by(ClaimDB.policy_number)))


async def test_un_sismo_que_cumple_la_regla_crea_y_liquida_un_siniestro_por_poliza_de_la_ciudad(async_client, postgres_session, policies):
    response = await post(async_client, event())

    assert response.status_code == 201
    created = await claims(postgres_session)
    assert [c.policy_number for c in created] == ["POL-2026-13001", "POL-2026-13002"]
    assert sorted(response.json()["claim_numbers"]) == sorted(c.claim_number for c in created)
    assert all(c.coverage_id == "disability" and c.status in ("SETTLED", "PAID") for c in created)


async def test_el_mismo_evento_no_crea_siniestros_otra_vez(async_client, postgres_session, policies):
    first = await post(async_client, event())
    again = await post(async_client, event())

    assert (first.status_code, again.status_code) == (201, 200)
    assert again.json()["duplicate"] is True
    assert again.json()["claim_numbers"] == first.json()["claim_numbers"]
    assert len(await claims(postgres_session)) == 2


async def test_dos_entregas_simultaneas_del_mismo_evento_dejan_un_juego_de_siniestros(async_client, postgres_session, policies):
    responses = await asyncio.gather(post(async_client, event()), post(async_client, event()))

    assert sorted(r.status_code for r in responses) == [200, 201]
    assert len(await claims(postgres_session)) == 2


async def test_un_sismo_bajo_el_umbral_no_crea_siniestros(async_client, postgres_session, policies):
    response = await post(async_client, event(magnitude=5.0))

    assert response.status_code == 201
    assert response.json()["claim_numbers"] == []
    assert await claims(postgres_session) == []


@pytest.mark.parametrize("headers", [
    lambda body: signed(body, secret="otra-llave-que-no-es-la-del-oraculo"),
    lambda body: signed(body, timestamp=int(time.time()) - 600),
    lambda body: {"X-Oracle-Timestamp": "no-es-una-hora", "X-Oracle-Signature": "abc"},
], ids=["firma-de-otra-llave", "marca-vieja", "marca-invalida"])
async def test_un_evento_sin_firma_valida_o_viejo_se_rechaza(async_client, postgres_session, policies, headers):
    body = event()

    response = await post(async_client, body, headers(body))

    assert response.status_code == 401
    assert await claims(postgres_session) == []


async def test_un_cuerpo_cambiado_despues_de_firmar_se_rechaza(async_client, postgres_session, policies):
    headers = signed(event(magnitude=5.0))

    response = await post(async_client, event(magnitude=9.0), headers)

    assert response.status_code == 401


async def test_sin_cabeceras_de_firma_responde_422(async_client):
    response = await async_client.post(URL, content=event())

    assert response.status_code == 422


async def test_un_evento_mal_formado_responde_422(async_client):
    body = json.dumps({"event_id": "x", "type": "HURACAN"}).encode()

    response = await post(async_client, body)

    assert response.status_code == 422


async def test_sin_llave_configurada_el_canal_esta_cerrado(async_client, monkeypatch):
    monkeypatch.setattr(settings, "ORACLE_SECRET", None)

    response = await post(async_client, event())

    assert response.status_code == 503


async def test_una_llave_vacia_cierra_el_canal_y_no_valida_firmas(async_client, monkeypatch):
    monkeypatch.setattr(settings, "ORACLE_SECRET", SecretStr(""))

    body = event()
    response = await post(async_client, body, signed(body, secret=""))

    assert response.status_code == 503


async def test_un_evento_demasiado_grande_se_rechaza(async_client):
    body = b"{" + b" " * 5000 + b"}"

    response = await post(async_client, body)

    assert response.status_code == 413


async def test_cada_siniestro_parametrico_deja_su_evento_en_el_outbox(async_client, postgres_session, policies):
    from app.core.outbox import OutboxMessageDB

    await post(async_client, event())

    events = await postgres_session.scalar(
        select(func.count()).select_from(OutboxMessageDB).where(OutboxMessageDB.event_type == "ClaimFiled")
    )
    assert events == 2


async def test_la_liquidacion_parametrica_deja_sus_cambios_de_estado_en_el_outbox_una_sola_vez(
    async_client, postgres_session, policies
):
    from app.core.outbox import OutboxMessageDB

    await post(async_client, event())
    await post(async_client, event())

    created = {c.claim_number: c.status for c in await claims(postgres_session)}
    changed = list(await postgres_session.scalars(
        select(OutboxMessageDB).where(OutboxMessageDB.event_type == "ClaimStatusChanged")
    ))
    kinds = sorted((e.aggregate_id, e.payload["kind"]) for e in changed)
    expected = sorted((number, kind) for number, status in created.items()
                      for kind in (("SETTLED", "PAID") if status == "PAID" else ("SETTLED",)))
    assert kinds == expected
