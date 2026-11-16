"""Consumidor del outbox: los avisos salen de la cola de eventos y no de la petición (ADR 0030)."""
import asyncio
import json
import uuid
from datetime import datetime, timedelta, timezone

import pytest
from httpx import AsyncClient
from sqlalchemy import func, select, text, update
from sqlalchemy.exc import OperationalError
from sqlalchemy.ext.asyncio import async_sessionmaker

from app.core import outbox
from app.core.outbox import (
    DONE, OutboxConsumer, OutboxMessageDB, OutboxPublisher, ProcessedMessageDB, SqsQueue, message_body,
)
from app.modules.claims.service import claim_service
from app.modules.notifications.service import (
    NotificationService, PushPreferences, PushTokenDB, notification_service,
)
from app.modules.policy.models import PolicyDB
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


class ListQueue:
    """Cola en memoria para provocar fallas que el emulador no simula."""

    def __init__(self, bodies, fail_delete=()):
        self.messages = [{"ReceiptHandle": f"r{i}", "Body": body} for i, body in enumerate(bodies, 1)]
        self.fail_delete = set(fail_delete)
        self.deleted, self.retried = [], []

    def receive(self, max_messages, wait_seconds):
        return self.messages

    def delete(self, receipt_handle):
        if receipt_handle in self.fail_delete:
            raise RuntimeError("SQS no responde")
        self.deleted.append(receipt_handle)

    def retry_later(self, receipt_handle, seconds):
        self.retried.append((receipt_handle, seconds))


def consumer_for(queue, sender, **options) -> OutboxConsumer:
    consumer = OutboxConsumer(lambda: queue, **{"retry_after_seconds": 0, "max_retry_seconds": 0, **options})
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
    sender = FakeSender()
    consumer = consumer_for(queue, sender)
    assert await publish(postgres_session, queue) == 1
    deleted = await drain(consumer, sessions)
    await async_client.post(f"{CLAIMS}/SIN-2026-00001/decision", headers=headers("operaciones@solventa.example"),
                            json={"decision": "APPROVE", "justification": JUSTIFICATION, "amount": 1_000_000})

    assert await publish(postgres_session, queue) == 2
    deleted += await drain(consumer, sessions)

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


async def test_un_evento_mal_formado_ajeno_al_outbox_o_ignorado_se_borra_y_uno_desconocido_vuelve(
    postgres_session, sessions, policy, queue
):
    await report(postgres_session, "SIN-2026-00001", "POL-2026-00001")
    filed = (await events(postgres_session, "ClaimFiled"))[0]
    queue.send(json.dumps({"message_id": str(uuid.uuid4()), "event_type": "Desconocido"}), "Desconocido", "x")
    queue.send("esto no es json", "ClaimFiled", "y")
    queue.send(json.dumps({"message_id": "1; DROP TABLE outbox", "event_type": "ClaimFiled"}), "ClaimFiled", "z")
    queue.send(json.dumps({"message_id": str(uuid.uuid4()), "event_type": "ClaimFiled"}), "ClaimFiled", "w")
    queue.send(json.dumps({"message_id": filed.id, "event_type": "ClaimStatusChanged"}), "ClaimStatusChanged", "v")
    queue.send(json.dumps({"message_id": str(uuid.uuid4()), "event_type": "PolicyIssued"}), "PolicyIssued", "u")
    queue.send(json.dumps({"message_id": str(uuid.uuid4()), "event_type": "PaymentApproved"}), "PaymentApproved", "t")
    sender = FakeSender()

    deleted = await drain(consumer_for(queue, sender), sessions)

    assert deleted == 6
    assert sender.sent == []
    assert await postgres_session.scalar(select(func.count()).select_from(ProcessedMessageDB)) == 0
    assert left_in(queue) == 1


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


async def test_un_siniestro_ya_decidido_no_recibe_el_aviso_de_recibido(
    async_client: AsyncClient, postgres_session, sessions, policy, queue
):
    await report(postgres_session, "SIN-2026-00001", "POL-2026-00001", coverage_id="death")
    await async_client.post(f"{CLAIMS}/SIN-2026-00001/decision", headers=headers("operaciones@solventa.example"),
                            json={"decision": "REJECT", "justification": JUSTIFICATION})
    await publish(postgres_session, queue)
    sender = FakeSender()

    deleted = await drain(consumer_for(queue, sender), sessions)

    assert deleted == 2
    assert [body for _, body, _ in sender.sent] == [
        "Hay una decisión sobre su siniestro SIN-2026-00001. Ábralo para ver el motivo."]


async def test_un_siniestro_recibido_hace_mas_de_un_dia_se_borra_sin_push(postgres_session, sessions, policy, queue):
    await report(postgres_session, "SIN-2026-00001", "POL-2026-00001")
    await postgres_session.execute(
        update(OutboxMessageDB).where(OutboxMessageDB.event_type == "ClaimFiled")
        .values(created_at=datetime.now(timezone.utc) - timedelta(hours=25))
    )
    await postgres_session.commit()
    await publish(postgres_session, queue)
    sender = FakeSender()

    deleted = await drain(consumer_for(queue, sender), sessions)

    assert deleted == 1
    assert sender.sent == []
    assert left_in(queue) == 0


async def test_un_recordatorio_de_una_version_anterior_de_la_poliza_no_se_envia(
    postgres_session, sessions, policy, queue, monkeypatch
):
    monkeypatch.setattr(policy_operations, "now", lambda: NOW + timedelta(days=345))
    await policy_operations.remind_renewals(postgres_session)
    row = await postgres_session.get(PolicyDB, "POL-2026-00001")
    row.version = (row.version or 1) + 1
    await postgres_session.commit()
    await publish(postgres_session, queue)
    sender = FakeSender()

    deleted = await drain(consumer_for(queue, sender), sessions)

    assert deleted == 1
    assert sender.sent == []


async def test_dos_replicas_con_el_mismo_mensaje_a_la_vez_envian_un_solo_push(postgres_session, sessions, policy):
    await report(postgres_session, "SIN-2026-00001", "POL-2026-00001")
    filed = (await events(postgres_session, "ClaimFiled"))[0]
    loop = asyncio.get_running_loop()
    entered, release = asyncio.Event(), asyncio.Event()

    class BlockingSender:
        def __init__(self):
            self.sent = []

        def send(self, tokens, title, body, data):
            loop.call_soon_threadsafe(entered.set)
            asyncio.run_coroutine_threadsafe(release.wait(), loop).result(timeout=10)
            self.sent.append(body)

    sender = BlockingSender()
    consumer = consumer_for(None, sender)

    async def release_while_the_other_waits():
        await entered.wait()
        await asyncio.sleep(0.5)
        release.set()

    first, second, _ = await asyncio.wait_for(asyncio.gather(
        consumer.process(sessions, message_body(filed)), consumer.process(sessions, message_body(filed)),
        release_while_the_other_waits(),
    ), timeout=20)

    assert (first, second) == (DONE, DONE)
    assert len(sender.sent) == 1
    assert await postgres_session.scalar(select(func.count()).select_from(ProcessedMessageDB)) == 1


async def test_una_falla_de_la_base_al_confirmar_no_detiene_el_lote_y_aplaza_el_mensaje(
    postgres_session, sessions, policy
):
    await report(postgres_session, "SIN-2026-00001", "POL-2026-00001")
    await report(postgres_session, "SIN-2026-00002", "POL-2026-00001", minutes=1)
    filed = await events(postgres_session, "ClaimFiled")
    queue = ListQueue([message_body(e) for e in filed])
    opened = []

    def failing_first_commit():
        session = sessions()
        if not opened:
            async def broken():
                raise OperationalError("COMMIT", {}, Exception("base caída"))
            session.commit = broken
        opened.append(session)
        return session

    consumer = consumer_for(queue, FakeSender(), retry_after_seconds=30, max_retry_seconds=900)
    done = await consumer.consume(failing_first_commit, wait_seconds=0)

    assert done == 1
    assert queue.retried == [("r1", 30)]
    assert queue.deleted == ["r2"]
    assert await postgres_session.scalar(select(func.count()).select_from(ProcessedMessageDB)) == 1


async def test_un_borrado_que_falla_no_detiene_el_lote(postgres_session, sessions, policy):
    await report(postgres_session, "SIN-2026-00001", "POL-2026-00001")
    await report(postgres_session, "SIN-2026-00002", "POL-2026-00001", minutes=1)
    filed = await events(postgres_session, "ClaimFiled")
    queue = ListQueue([message_body(e) for e in filed], fail_delete={"r1"})
    sender = FakeSender()

    done = await consumer_for(queue, sender).consume(sessions, wait_seconds=0)

    assert done == 1
    assert queue.deleted == ["r2"]
    assert queue.retried == []
    assert len(sender.sent) == 2


class StaleTokenSender(FakeSender):
    """Firebase entrega el aviso y responde que el token ya no sirve."""

    def send(self, tokens, title, body, data):
        super().send(tokens, title, body, data)
        return list(tokens)


async def test_el_token_rechazado_se_borra_en_la_misma_transaccion_que_marca_el_mensaje(
    postgres_session, sessions, policy
):
    await report(postgres_session, "SIN-2026-00001", "POL-2026-00001")
    filed = (await events(postgres_session, "ClaimFiled"))[0]
    sender = StaleTokenSender()

    outcome = await consumer_for(None, sender).process(sessions, message_body(filed))

    async with sessions() as fresh:
        assert await fresh.get(PushTokenDB, TOKEN) is None
        assert await fresh.scalar(select(func.count()).select_from(ProcessedMessageDB)) == 1
    assert outcome == DONE
    assert len(sender.sent) == 1


async def test_si_el_borrado_del_token_falla_el_mensaje_igual_se_confirma(postgres_session, sessions, policy, caplog):
    await report(postgres_session, "SIN-2026-00001", "POL-2026-00001")
    filed = (await events(postgres_session, "ClaimFiled"))[0]
    await postgres_session.execute(text(
        "CREATE FUNCTION impedir_borrado() RETURNS trigger LANGUAGE plpgsql AS "
        "$$ BEGIN RAISE EXCEPTION 'borrado bloqueado'; END $$"
    ))
    await postgres_session.execute(text(
        "CREATE TRIGGER impedir_borrado BEFORE DELETE ON push_tokens FOR EACH ROW EXECUTE FUNCTION impedir_borrado()"
    ))
    await postgres_session.commit()
    sender = StaleTokenSender()

    with caplog.at_level("WARNING", logger="solventa.notifications"):
        outcome = await consumer_for(None, sender).process(sessions, message_body(filed))

    async with sessions() as fresh:
        assert await fresh.get(PushTokenDB, TOKEN) is not None
        assert await fresh.scalar(select(func.count()).select_from(ProcessedMessageDB)) == 1
    assert outcome == DONE
    assert len(sender.sent) == 1
    assert "No se pudieron borrar los tokens que Firebase rechazó (DBAPIError)" in caplog.text
    assert TOKEN not in caplog.text
