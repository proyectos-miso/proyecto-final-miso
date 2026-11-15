"""Transactional outbox: eventos de dominio guardados en la misma transacción que su agregado (ASR-05) y un publicador
que los envía a SQS al menos una vez (HU-MOV-10, RC-14)."""
import asyncio
import json
import logging
import uuid
from datetime import datetime, timezone
from functools import lru_cache
from typing import Callable, Optional, Protocol

from sqlalchemy import JSON, Column, DateTime, Integer, String, func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import settings
from app.core.database import Base

logger = logging.getLogger("solventa.outbox")

PUBLISH_EVERY_SECONDS = 5
BATCH = 50


class OutboxMessageDB(Base):
    """Evento pendiente de publicar hacia SQS; lo escribe el módulo dueño del agregado al confirmar su cambio."""
    __tablename__ = "outbox"

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    aggregate_id = Column(String(64), nullable=False)
    event_type = Column(String(64), nullable=False)
    payload = Column(JSON, nullable=False)
    status = Column(String(16), nullable=False, default="PENDING")
    created_at = Column(DateTime(timezone=True), server_default=func.now(), nullable=False)
    attempts = Column(Integer, nullable=False, default=0, server_default="0")
    published_at = Column(DateTime(timezone=True), nullable=True)


class MessageQueue(Protocol):
    def send(self, body: str, event_type: str, message_id: str) -> None: ...


class SqsQueue:
    def __init__(self, queue: str, region: str, endpoint_url: Optional[str] = None):
        import boto3

        # Como el almacenamiento (storage.py): con emulador, credenciales de prueba y una sesión propia.
        emulator = {"aws_access_key_id": "test", "aws_secret_access_key": "test"} if endpoint_url else {}
        self.client = boto3.session.Session().client("sqs", region_name=region, endpoint_url=endpoint_url, **emulator)
        self.queue = queue
        self._url: Optional[str] = queue if queue.startswith("http") else None

    def url(self) -> str:
        if self._url is None:
            if settings.AWS_ENDPOINT_URL:
                self.client.create_queue(QueueName=self.queue)
            self._url = self.client.get_queue_url(QueueName=self.queue)["QueueUrl"]
        return self._url

    def send(self, body: str, event_type: str, message_id: str) -> None:
        self.client.send_message(
            QueueUrl=self.url(), MessageBody=body,
            MessageAttributes={
                "event_type": {"DataType": "String", "StringValue": event_type},
                "message_id": {"DataType": "String", "StringValue": message_id},
            },
        )


@lru_cache
def get_message_queue() -> MessageQueue:
    return SqsQueue(settings.SQS_QUEUE_EVENTS, settings.AWS_REGION, settings.AWS_ENDPOINT_URL)


def message_body(message: OutboxMessageDB) -> str:
    """Solo identificadores: quien consuma la cola consulta el resto con su permiso (no viajan datos personales)."""
    return json.dumps({
        "message_id": message.id,
        "event_type": message.event_type,
        "aggregate_id": message.aggregate_id,
        "occurred_at": message.created_at.isoformat() if message.created_at else None,
    })


class OutboxPublisher:
    def __init__(self, queue_factory: Callable[[], MessageQueue] = get_message_queue,
                 now: Callable[[], datetime] = lambda: datetime.now(timezone.utc)):
        self.queue_factory = queue_factory
        self.now = now

    async def publish_pending(self, session: AsyncSession, limit: int = BATCH) -> int:
        """Publica los pendientes en orden; uno que falla queda pendiente para el siguiente ciclo (al menos una vez)."""
        pending = list(await session.scalars(
            select(OutboxMessageDB).where(OutboxMessageDB.status == "PENDING")
            .order_by(OutboxMessageDB.created_at).limit(limit).with_for_update(skip_locked=True)
        ))
        published = 0
        for message in pending:
            try:
                await asyncio.to_thread(self.queue_factory().send, message_body(message), message.event_type, message.id)
            except Exception as exc:
                message.attempts = (message.attempts or 0) + 1
                logger.warning("No se pudo publicar el evento del outbox (%s)", type(exc).__name__)
                break
            message.status, message.published_at = "PUBLISHED", self.now()
            published += 1
        await session.commit()
        return published


outbox_publisher = OutboxPublisher()
