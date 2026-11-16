"""Consumidor del outbox: los avisos salen de la cola de eventos y no de la petición (ADR 0030)."""
import json
import uuid
from datetime import timedelta

import pytest
from httpx import AsyncClient
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import async_sessionmaker

from app.core import outbox
from app.core.outbox import OutboxConsumer, OutboxMessageDB, OutboxPublisher, ProcessedMessageDB, SqsQueue, message_body
from app.modules.claims.service import claim_service
from app.modules.notifications.service import NotificationService, PushPreferences, notification_service
from app.modules.policy.operations import policy_operations
from tests.integration.claims_fixtures import FLOCI, report
from tests.integration.policy_search_fixtures import NOW, add_policies, headers, policy_row, seed_partners_and_users

pytestmark = pytest.mark.integration

DOCUMENT = "1020304050"
TOKEN = "fcm-token-de-prueba-0001"
CLAIMS = "/api/v1/experience/claims"
JUSTIFICATION = "Se verificó el certificado de defunción y la cobertura vigente al momento del hecho."


class FakeSender:
    def __init__(self):
        self.sent = []

    def send(self, tokens, title, body, data):
        self.sent.append((tokens, body, data))


class FailingSender:
    def __init__(self):
        self.calls = 0

    def send(self, tokens, title, body, data):
        self.calls += 1
        raise RuntimeError("Firebase no responde")


@pytest.fixture
def queue(monkeypatch):
    monkeypatch.setattr(outbox.settings, "AWS_ENDPOINT_URL", FLOCI)
    return SqsQueue(f"eventos-prueba-{uuid.uuid4().hex[:8]}", "us-east-1", FLOCI)


@pytest.fixture
def sessions(postgres_session):
    return async_sessionmaker(postgres_session.bind, expire_on_commit=False)


@pytest.fixture
async def policy(postgres_session):
    await seed_partners_and_users(postgres_session)
    await claim_service.seed_experts(postgres_session)
    await add_policies(postgres_session, [policy_row("POL-2026-00001", DOCUMENT, holder="Carolina Prueba Gómez",
                                                     plan="premium")])
    await NotificationService(lambda: None).register(postgres_session, DOCUMENT, TOKEN)


def consumer_for(queue, sender) -> OutboxConsumer:
    consumer = OutboxConsumer(lambda: queue, retry_after_seconds=0)
    for event_type, handler in NotificationService(lambda: sender).event_handlers().items():
        consumer.register(event_type, handler)
    return consumer


async def publish(session, queue) -> int:
    return await OutboxPublisher(lambda: queue).publish_pending(session)


async def drain(consumer, sessions) -> int:
    """Consume hasta que la cola queda vacía; devuelve cuántos mensajes se borraron."""
    deleted = 0
    for _ in range(5):
        done = await consumer.consume(sessions, wait_seconds=1)
        deleted += done
        if done == 0:
            break
    return deleted


def left_in(queue) -> int:
    response = queue.client.receive_message(QueueUrl=queue.url(), MaxNumberOfMessages=10, WaitTimeSeconds=1)
    return len(response.get("Messages", []))


async def events(session, event_type):
    return list(await session.scalars(
        select(OutboxMessageDB).where(OutboxMessageDB.event_type == event_type).order_by(OutboxMessageDB.created_at)
    ))


async def test_la_decision_del_analista_no_llama_a_firebase_y_solo_escribe_el_evento(
    async_client: AsyncClient, postgres_session, policy, monkeypatch
):
    await report(postgres_session, "SIN-2026-00001", "POL-2026-00001", coverage_id="death")
    calls = []
    monkeypatch.setattr(notification_service, "sender_factory", lambda: calls.append("firebase"))

    response = await async_client.post(f"{CLAIMS}/SIN-2026-00001/decision", headers=headers("operaciones@solventa.example"),
                                       json={"decision": "REJECT", "justification": JUSTIFICATION})

    changed = await events(postgres_session, "ClaimStatusChanged")
    assert response.status_code == 204
    assert calls == []
    assert [(e.aggregate_id, e.payload) for e in changed] == [
        ("SIN-2026-00001", {"claim_number": "SIN-2026-00001", "policy_number": "POL-2026-00001", "kind": "REJECTED"})]
    assert DOCUMENT not in json.dumps([e.payload for e in changed])


async def test_el_consumidor_envia_cada_push_con_su_categoria_y_el_pago_va_a_pagos(
    async_client: AsyncClient, postgres_session, sessions, policy, queue
):
    await report(postgres_session, "SIN-2026-00001", "POL-2026-00001", coverage_id="death")
    await async_client.post(f"{CLAIMS}/SIN-2026-00001/decision", headers=headers("operaciones@solventa.example"),
                            json={"decision": "APPROVE", "justification": JUSTIFICATION, "amount": 1_000_000})
    sender = FakeSender()

    assert await publish(postgres_session, queue) == 3
    deleted = await drain(consumer_for(queue, sender), sessions)

    assert deleted == 3
    assert sorted((body, data["category"]) for _, body, data in sender.sent) == [
        ("El pago de su siniestro SIN-2026-00001 fue realizado.", "payments"),
        ("Recibimos su siniestro SIN-2026-00001. Le avisaremos cuando haya novedades.", "claims"),
        ("Su siniestro SIN-2026-00001 fue aprobado.", "claims"),
    ]
    assert all(tokens == [TOKEN] and data["claim_number"] == "SIN-2026-00001" for tokens, _, data in sender.sent)
    assert left_in(queue) == 0


async def test_un_mensaje_repetido_no_envia_dos_pushes(postgres_session, sessions, policy, queue):
    await report(postgres_session, "SIN-2026-00001", "POL-2026-00001")
    filed = (await events(postgres_session, "ClaimFiled"))[0]
    queue.send(message_body(filed), filed.event_type, filed.id)
    queue.send(message_body(filed), filed.event_type, filed.id)
    sender = FakeSender()

    deleted = await drain(consumer_for(queue, sender), sessions)

    assert deleted == 2
    assert len(sender.sent) == 1
    assert await postgres_session.scalar(select(func.count()).select_from(ProcessedMessageDB)) == 1
    assert left_in(queue) == 0


async def test_una_falla_de_firebase_no_borra_el_mensaje_y_se_entrega_al_reintentar(
    postgres_session, sessions, policy, queue
):
    await report(postgres_session, "SIN-2026-00001", "POL-2026-00001")
    await publish(postgres_session, queue)
    failing, healthy = FailingSender(), FakeSender()

    first = await consumer_for(queue, failing).consume(sessions, wait_seconds=1)
    processed = await postgres_session.scalar(select(func.count()).select_from(ProcessedMessageDB))
    retried = await drain(consumer_for(queue, healthy), sessions)

    assert (first, failing.calls, processed) == (0, 1, 0)
    assert retried == 1
    assert [body for _, body, _ in healthy.sent] == [
        "Recibimos su siniestro SIN-2026-00001. Le avisaremos cuando haya novedades."]
    assert left_in(queue) == 0


async def test_el_recordatorio_de_renovacion_y_el_siniestro_recibido_producen_sus_pushes(
    postgres_session, sessions, policy, queue, monkeypatch
):
    monkeypatch.setattr(policy_operations, "now", lambda: NOW + timedelta(days=345))
    await policy_operations.remind_renewals(postgres_session)
    await report(postgres_session, "SIN-2026-00001", "POL-2026-00001")
    await publish(postgres_session, queue)
    sender = FakeSender()

    await drain(consumer_for(queue, sender), sessions)

    by_type = {data["type"]: (body, data) for _, body, data in sender.sent}
    renewal_body, renewal = by_type["RENEWAL_REMINDER"]
    assert renewal == {"type": "RENEWAL_REMINDER", "policy_number": "POL-2026-00001", "category": "renewals"}
    assert "POL-2026-00001" in renewal_body and f"{NOW + timedelta(days=365):%d/%m/%Y}" in renewal_body
    assert by_type["CLAIM_STATUS"][1] == {"type": "CLAIM_STATUS", "claim_number": "SIN-2026-00001", "category": "claims"}


async def test_un_evento_desconocido_mal_formado_o_ajeno_al_outbox_se_borra_sin_push(
    postgres_session, sessions, policy, queue
):
    await report(postgres_session, "SIN-2026-00001", "POL-2026-00001")
    filed = (await events(postgres_session, "ClaimFiled"))[0]
    queue.send(json.dumps({"message_id": str(uuid.uuid4()), "event_type": "Desconocido"}), "Desconocido", "x")
    queue.send("esto no es json", "ClaimFiled", "y")
    queue.send(json.dumps({"message_id": "1; DROP TABLE outbox", "event_type": "ClaimFiled"}), "ClaimFiled", "z")
    queue.send(json.dumps({"message_id": str(uuid.uuid4()), "event_type": "ClaimFiled"}), "ClaimFiled", "w")
    queue.send(json.dumps({"message_id": filed.id, "event_type": "ClaimStatusChanged"}), "ClaimStatusChanged", "v")
    sender = FakeSender()

    deleted = await drain(consumer_for(queue, sender), sessions)

    assert deleted == 5
    assert sender.sent == []
    assert await postgres_session.scalar(select(func.count()).select_from(ProcessedMessageDB)) == 0
    assert left_in(queue) == 0


async def test_una_categoria_apagada_o_un_telefono_sin_token_cuentan_como_procesados(
    postgres_session, sessions, policy, queue
):
    service = NotificationService(lambda: None)
    await service.save_preferences(postgres_session, DOCUMENT, PushPreferences(claims=False, renewals=True, payments=True))
    await add_policies(postgres_session, [policy_row("POL-2026-00002", "7788990011")])
    await report(postgres_session, "SIN-2026-00001", "POL-2026-00001")
    await report(postgres_session, "SIN-2026-00002", "POL-2026-00002")
    await publish(postgres_session, queue)
    sender = FakeSender()

    deleted = await drain(consumer_for(queue, sender), sessions)

    assert deleted == 2
    assert sender.sent == []
    assert await postgres_session.scalar(select(func.count()).select_from(ProcessedMessageDB)) == 2
    assert left_in(queue) == 0
