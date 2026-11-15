"""Preferencias de avisos por categoría contra PostgreSQL (mejora FE-MOV-04)."""
from datetime import datetime, timezone

import pytest
from httpx import AsyncClient
from sqlalchemy import text

from app.core.security import create_access_token
from app.modules.notifications.service import NotificationService, PushPreferences
from tests.integration.test_insured_claims_api import TOKEN, FakeSender
from tests.integration.test_insured_policies_api import MARIA_DOCUMENT, insured_headers

pytestmark = [pytest.mark.integration, pytest.mark.usefixtures("seeded_insured")]

URL = "/api/v1/insured/devices/push-preferences"
NOW = datetime(2026, 11, 14, 12, 0, tzinfo=timezone.utc)


async def with_phone(session) -> tuple:
    sender = FakeSender()
    service = NotificationService(lambda: sender)
    await service.register(session, MARIA_DOCUMENT, TOKEN)
    return service, sender


async def test_sin_preferencia_guardada_se_aceptan_todas_las_categorias(async_client: AsyncClient):
    response = await async_client.get(URL, headers=insured_headers())

    assert response.status_code == 200
    assert response.json() == {"claims": True, "renewals": True, "payments": True}


async def test_guarda_las_preferencias_y_las_devuelve(async_client: AsyncClient):
    saved = await async_client.put(URL, headers=insured_headers(), json={"claims": True, "renewals": False, "payments": False})
    read = await async_client.get(URL, headers=insured_headers())

    assert saved.status_code == 200
    assert read.json() == {"claims": True, "renewals": False, "payments": False}


async def test_un_cuerpo_invalido_o_sin_sesion_se_rechaza(async_client: AsyncClient):
    advisor = {"Authorization": f"Bearer {create_access_token(subject='asesor@solventa.com', role='advisor')}"}

    invalid = [
        (await async_client.put(URL, headers=insured_headers(), json=body)).status_code
        for body in (
            {"claims": "si", "renewals": True, "payments": True},
            {"claims": 0, "renewals": True, "payments": True},
            {"payments": False},
            {"claims": True, "renewals": True, "payments": True, "document_number": "1"},
        )
    ]
    anonymous = await async_client.put(URL, json={"claims": False, "renewals": True, "payments": True})
    portal = await async_client.get(URL, headers=advisor)

    assert invalid == [422, 422, 422, 422]
    assert (anonymous.status_code, portal.status_code) == (401, 403)


async def test_una_categoria_apagada_no_se_envia_y_las_demas_si(postgres_session):
    service, sender = await with_phone(postgres_session)
    await service.save_preferences(postgres_session, MARIA_DOCUMENT, PushPreferences(claims=True, renewals=False, payments=False))

    assigned = await service.claim_changed(postgres_session, MARIA_DOCUMENT, "SIN-2026-00001", "ASSIGNED")
    paid = await service.claim_changed(postgres_session, MARIA_DOCUMENT, "SIN-2026-00001", "PAID")
    renewal = await service.renewal_due(postgres_session, MARIA_DOCUMENT, "POL-2026-00001", NOW)

    assert (assigned, paid, renewal) == (True, False, False)
    assert [sent[2]["category"] for sent in sender.sent] == ["claims"]


async def test_el_push_lleva_su_categoria_y_el_pago_es_de_pagos(postgres_session):
    service, sender = await with_phone(postgres_session)

    await service.claim_changed(postgres_session, MARIA_DOCUMENT, "SIN-2026-00001", "PAID")
    await service.renewal_due(postgres_session, MARIA_DOCUMENT, "POL-2026-00001", NOW)

    assert sender.sent[0][2] == {"type": "CLAIM_STATUS", "claim_number": "SIN-2026-00001", "category": "payments"}
    assert sender.sent[1][2]["category"] == "renewals"


async def test_si_falla_la_lectura_de_la_preferencia_el_aviso_no_rompe_la_operacion(postgres_session, monkeypatch):
    service, sender = await with_phone(postgres_session)

    async def broken(session, _document):
        await session.execute(text("SELECT * FROM tabla_que_no_existe"))

    monkeypatch.setattr(service, "preferences", broken)

    assert await service.claim_changed(postgres_session, MARIA_DOCUMENT, "SIN-2026-00001", "ASSIGNED") is False
    assert sender.sent == []
    assert await postgres_session.scalar(text("SELECT 1")) == 1


async def test_el_segundo_guardado_reemplaza_al_primero_con_upsert(postgres_session):
    service = NotificationService(lambda: None)

    await service.save_preferences(postgres_session, MARIA_DOCUMENT, PushPreferences(claims=False))
    await service.save_preferences(postgres_session, MARIA_DOCUMENT, PushPreferences(payments=False))

    assert await service.preferences(postgres_session, MARIA_DOCUMENT) == PushPreferences(payments=False)
