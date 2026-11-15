"""Publicador del outbox hacia SQS (HU-MOV-10, RC-14)."""
import json
import uuid

import pytest
from sqlalchemy import select

from app.core.outbox import OutboxMessageDB, OutboxPublisher, SqsQueue, message_body
from tests.integration.claims_fixtures import FLOCI

pytestmark = pytest.mark.integration


class RecordingQueue:
    def __init__(self, fail_after: int = None):
        self.sent = []
        self.fail_after = fail_after

    def send(self, body, event_type, message_id):
        if self.fail_after is not None and len(self.sent) >= self.fail_after:
            raise RuntimeError("SQS no responde")
        self.sent.append(json.loads(body))


async def add_messages(session, count: int) -> list:
    messages = [OutboxMessageDB(aggregate_id=f"SIN-2026-0000{i}", event_type="ClaimFiled",
                                payload={"claim_number": f"SIN-2026-0000{i}", "documento": "1020304050"})
                for i in range(1, count + 1)]
    session.add_all(messages)
    await session.commit()
    return [m.id for m in messages]


async def statuses(session) -> list:
    rows = await session.scalars(select(OutboxMessageDB).order_by(OutboxMessageDB.aggregate_id))
    return [(m.status, m.attempts) for m in rows]


async def test_publica_los_pendientes_y_los_marca(postgres_session):
    await add_messages(postgres_session, 3)
    queue = RecordingQueue()

    published = await OutboxPublisher(lambda: queue).publish_pending(postgres_session)

    assert published == 3
    assert await statuses(postgres_session) == [("PUBLISHED", 0)] * 3
    assert await OutboxPublisher(lambda: queue).publish_pending(postgres_session) == 0


async def test_si_sqs_falla_el_mensaje_queda_pendiente_y_se_reintenta(postgres_session):
    await add_messages(postgres_session, 2)

    failing, healthy = RecordingQueue(fail_after=1), RecordingQueue()

    first = await OutboxPublisher(lambda: failing).publish_pending(postgres_session)
    pending = await statuses(postgres_session)
    again = await OutboxPublisher(lambda: healthy).publish_pending(postgres_session)

    assert (first, again) == (1, 1)
    assert pending == [("PUBLISHED", 0), ("PENDING", 1)]
    assert [s for s, _ in await statuses(postgres_session)] == ["PUBLISHED", "PUBLISHED"]


async def test_el_mensaje_solo_lleva_identificadores(postgres_session):
    await add_messages(postgres_session, 1)
    message = await postgres_session.scalar(select(OutboxMessageDB))

    body = json.loads(message_body(message))

    assert set(body) == {"message_id", "event_type", "aggregate_id", "occurred_at"}
    assert "1020304050" not in json.dumps(body)


async def test_llega_a_la_cola_sqs_del_emulador(postgres_session):
    queue = SqsQueue(f"outbox-prueba-{uuid.uuid4().hex[:8]}", "us-east-1", FLOCI)
    from app.core import outbox
    original = outbox.settings.AWS_ENDPOINT_URL
    outbox.settings.AWS_ENDPOINT_URL = FLOCI
    try:
        ids = await add_messages(postgres_session, 1)
        await OutboxPublisher(lambda: queue).publish_pending(postgres_session)
        received = queue.client.receive_message(QueueUrl=queue.url(), MessageAttributeNames=["All"], WaitTimeSeconds=1)
    finally:
        outbox.settings.AWS_ENDPOINT_URL = original

    message = received["Messages"][0]
    assert json.loads(message["Body"])["message_id"] == ids[0]
    assert message["MessageAttributes"]["event_type"]["StringValue"] == "ClaimFiled"


async def test_qr_01_un_lote_sale_en_segundos_y_el_ciclo_es_menor_a_10_segundos(postgres_session):
    import time

    from app.core.outbox import BATCH, PUBLISH_EVERY_SECONDS

    await add_messages(postgres_session, 9)
    queue = RecordingQueue()

    start = time.perf_counter()
    published = await OutboxPublisher(lambda: queue).publish_pending(postgres_session, limit=BATCH)

    assert published == 9
    assert time.perf_counter() - start < 2
    assert PUBLISH_EVERY_SECONDS + 2 < 10
