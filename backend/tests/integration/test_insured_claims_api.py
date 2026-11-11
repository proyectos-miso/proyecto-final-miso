"""Siniestros, carátula, renovación y avisos del asegurado contra PostgreSQL (HU-MOV-11)."""
import time
from datetime import datetime, timedelta, timezone

import pytest
from httpx import AsyncClient
from sqlalchemy import func, insert, select

from app.core.security import create_access_token
from app.modules.claims.models import ClaimDB, ClaimEventDB
from app.modules.notifications.service import NotificationService, PushTokenDB
from app.modules.policy.models import PolicyDB, PolicyEventDB
from app.modules.policy.operations import policy_operations
from tests.integration.test_insured_policies_api import MARIA_DOCUMENT, OTHER_DOCUMENT, add_policy, insured_headers

pytestmark = [pytest.mark.integration, pytest.mark.usefixtures("seeded_insured")]

NOW = datetime(2026, 10, 22, 12, 0, tzinfo=timezone.utc)
URL = "/api/v1/insured/claims"
TOKEN = "fcm-token-de-prueba-0001"


@pytest.fixture(autouse=True)
def fixed_clock(monkeypatch):
    monkeypatch.setattr(policy_operations, "now", lambda: NOW)


class FakeSender:
    def __init__(self):
        self.sent = []

    def send(self, tokens, title, body, data):
        self.sent.append((tokens, body, data))


async def add_claim(session, number, policy, status, events, origin="APP", reported_at=NOW):
    session.add(ClaimDB(claim_number=number, policy_number=policy, status=status, reported_at=reported_at, origin=origin))
    await session.flush()
    for offset, (kind, justification) in enumerate(events):
        session.add(ClaimEventDB(claim_number=number, kind=kind, actor="Analista de prueba",
                                 justification=justification, created_at=reported_at + timedelta(hours=offset)))
    await session.commit()


async def test_lista_solo_los_siniestros_propios_del_mas_reciente_al_mas_antiguo(async_client: AsyncClient, postgres_session):
    await add_policy(postgres_session, "POL-2026-11001", MARIA_DOCUMENT, "ACTIVE", NOW + timedelta(days=200))
    await add_policy(postgres_session, "POL-2026-11002", OTHER_DOCUMENT, "ACTIVE", NOW + timedelta(days=200))
    await add_claim(postgres_session, "SIN-2026-000001", "POL-2026-11001", "IN_REVIEW",
                    [("REPORTED", None), ("ASSIGNED", None)], reported_at=NOW - timedelta(days=5))
    await add_claim(postgres_session, "SIN-2026-000002", "POL-2026-11001", "PAID",
                    [("REPORTED", None), ("ASSIGNED", None), ("SETTLED", "Aprobado"), ("PAID", None)])
    await add_claim(postgres_session, "SIN-2026-000003", "POL-2026-11002", "REPORTED", [("REPORTED", None)])

    response = await async_client.get(URL, headers=insured_headers())

    assert response.status_code == 200
    assert response.headers["cache-control"] == "no-store"
    claims = response.json()["claims"]
    assert [c["claim_number"] for c in claims] == ["SIN-2026-000002", "SIN-2026-000001"]
    assert [s["label"] for s in claims[0]["timeline"]] == ["Recibido", "En evaluación", "Aprobado", "Pagado"]
    assert claims[0]["status_label"] == "Pagado"
    assert claims[1]["review_channel"] is None


async def test_rechazado_trae_motivo_y_canal_de_revision(async_client: AsyncClient, postgres_session):
    await add_policy(postgres_session, "POL-2026-11003", MARIA_DOCUMENT, "ACTIVE", NOW + timedelta(days=200))
    await add_claim(postgres_session, "SIN-2026-000004", "POL-2026-11003", "REJECTED",
                    [("REPORTED", None), ("REJECTED", "El evento ocurrió antes de la vigencia.")], origin="PARAMETRIC")

    claim = (await async_client.get(URL, headers=insured_headers())).json()["claims"][0]

    assert claim["rejection_reason"] == "El evento ocurrió antes de la vigencia."
    assert "revisiones@solventa.example" in claim["review_channel"]
    assert "automáticamente" in claim["timeline"][0]["explanation"]


async def test_sin_siniestros_devuelve_lista_vacia(async_client: AsyncClient):
    response = await async_client.get(URL, headers=insured_headers())

    assert response.json()["claims"] == []


async def test_sin_token_o_con_token_del_portal_se_rechaza(async_client: AsyncClient):
    advisor = create_access_token(subject="asesor@solventa.com", role="advisor")

    assert (await async_client.get(URL)).status_code == 401
    assert (await async_client.get(URL, headers={"Authorization": f"Bearer {advisor}"})).status_code == 403


async def test_caratula_de_otra_persona_responde_404(async_client: AsyncClient, postgres_session):
    await add_policy(postgres_session, "POL-2026-11004", OTHER_DOCUMENT, "ACTIVE", NOW + timedelta(days=200))

    response = await async_client.get("/api/v1/insured/policies/POL-2026-11004/caratula", headers=insured_headers())

    assert response.status_code == 404


async def test_renovacion_solo_dentro_de_los_30_dias(async_client: AsyncClient, postgres_session):
    await add_policy(postgres_session, "POL-2026-11005", MARIA_DOCUMENT, "ACTIVE", NOW + timedelta(days=200))
    await add_policy(postgres_session, "POL-2026-11006", MARIA_DOCUMENT, "ACTIVE", NOW + timedelta(days=10))

    early = await async_client.post("/api/v1/insured/policies/POL-2026-11005/renewal", headers=insured_headers())
    renewed = await async_client.post("/api/v1/insured/policies/POL-2026-11006/renewal", headers=insured_headers())

    assert early.status_code == 409
    assert renewed.status_code == 200
    postgres_session.expire_all()
    policy = await postgres_session.get(PolicyDB, "POL-2026-11006")
    assert policy.valid_until.date() == (NOW + timedelta(days=10)).replace(year=2027).date()
    assert datetime.fromisoformat(renewed.json()["valid_until"]).date() == policy.valid_until.date()


async def test_renovar_poliza_de_otra_persona_responde_404(async_client: AsyncClient, postgres_session):
    await add_policy(postgres_session, "POL-2026-11007", OTHER_DOCUMENT, "ACTIVE", NOW + timedelta(days=10))

    response = await async_client.post("/api/v1/insured/policies/POL-2026-11007/renewal", headers=insured_headers())

    assert response.status_code == 404


async def test_vencida_pasa_a_expired_y_al_renovar_vuelve_a_emitida(postgres_session):
    await add_policy(postgres_session, "POL-2026-11008", MARIA_DOCUMENT, "ACTIVE", NOW - timedelta(days=1))

    assert await policy_operations.expire_due(postgres_session) == 1
    await policy_operations.renew(postgres_session, "POL-2026-11008", "Prueba")

    postgres_session.expire_all()
    assert (await postgres_session.get(PolicyDB, "POL-2026-11008")).status == "ISSUED"


async def test_aviso_de_vencimiento_se_envia_una_sola_vez(postgres_session):
    await add_policy(postgres_session, "POL-2026-11009", MARIA_DOCUMENT, "ACTIVE", NOW + timedelta(days=20))
    sent = []

    async def notify(session, document, policy_number, valid_until):
        sent.append((document, policy_number))

    await policy_operations.remind_renewals(postgres_session, notify)
    await policy_operations.remind_renewals(postgres_session, notify)

    assert sent == [(MARIA_DOCUMENT, "POL-2026-11009")]
    reminders = await postgres_session.scalar(
        select(func.count()).select_from(PolicyEventDB).where(PolicyEventDB.kind == "RENEWAL_REMINDER")
    )
    assert reminders == 1


async def test_registra_token_y_envia_push_sin_datos_sensibles(async_client: AsyncClient, postgres_session):
    response = await async_client.post("/api/v1/insured/devices/push-token", json={"token": TOKEN},
                                       headers=insured_headers())
    sender = FakeSender()

    sent = await NotificationService(lambda: sender).claim_changed(postgres_session, MARIA_DOCUMENT, "SIN-2026-000009", "SETTLED")

    assert response.status_code == 204
    assert (await postgres_session.get(PushTokenDB, TOKEN)).document_number == MARIA_DOCUMENT
    assert sent and sender.sent[0][0] == [TOKEN]
    assert sender.sent[0][2] == {"type": "CLAIM_STATUS", "claim_number": "SIN-2026-000009"}


async def test_token_invalido_se_rechaza(async_client: AsyncClient):
    response = await async_client.post("/api/v1/insured/devices/push-token", json={"token": "<script>alert(1)</script>"},
                                       headers=insured_headers())

    assert response.status_code == 422


async def test_sin_firebase_no_falla(postgres_session):
    assert await NotificationService(lambda: None).claim_changed(postgres_session, MARIA_DOCUMENT, "SIN-1", "PAID") is False


async def test_qr_01_p95_bajo_150_ms_con_10000_siniestros(async_client: AsyncClient, postgres_session):
    await add_policy(postgres_session, "POL-2026-11010", MARIA_DOCUMENT, "ACTIVE", NOW + timedelta(days=200))
    await add_policy(postgres_session, "POL-2026-11011", OTHER_DOCUMENT, "ACTIVE", NOW + timedelta(days=200))
    await postgres_session.execute(insert(ClaimDB), [
        {"claim_number": f"SIN-2026-{i:06d}", "policy_number": "POL-2026-11011" if i > 3 else "POL-2026-11010",
         "status": "REPORTED", "reported_at": NOW, "origin": "APP"} for i in range(1, 10001)
    ])
    await postgres_session.commit()
    headers = insured_headers()
    await async_client.get(URL, headers=headers)

    times = []
    for _ in range(40):
        start = time.perf_counter()
        response = await async_client.get(URL, headers=headers)
        times.append(time.perf_counter() - start)
    times.sort()

    assert len(response.json()["claims"]) == 3
    assert times[int(len(times) * 0.95) - 1] < 0.150
