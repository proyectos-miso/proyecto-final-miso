"""Transactional outbox: eventos de dominio guardados en la misma transacción que su agregado (ASR-05), un publicador
que los envía a SQS al menos una vez (HU-MOV-10, RC-14) y un consumidor que los entrega a sus manejadores en efecto una
vez por mensaje (ADR 0030)."""
import asyncio
import json
import logging
import re
import uuid
from dataclasses import dataclass
from datetime import datetime, timezone
from functools import lru_cache
from typing import Any, Awaitable, Callable, Dict, List, Optional, Protocol

from sqlalchemy import JSON, Column, DateTime, Integer, String, func, select
from sqlalchemy.dialects.postgresql import insert
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker

from app.core.config import settings
from app.core.database import Base

logger = logging.getLogger("solventa.outbox")

PUBLISH_EVERY_SECONDS = 5
BATCH = 50
RECEIVE_WAIT_SECONDS = 10
RECEIVE_MAX_MESSAGES = 10
RETRY_AFTER_SECONDS = 30
MAX_RETRY_SECONDS = 15 * 60
# Eventos que hoy no necesitan consumidor: se borran. Un tipo que no está aquí ni tiene manejador se reintenta y, si
# ninguna réplica lo maneja, termina en la DLQ (por ejemplo, durante un despliegue escalonado).
IGNORED_EVENT_TYPES = frozenset({"PolicyIssued", "PaymentApproved", "ClaimDocumentsAdded"})
DONE, RETRY, UNHANDLED = "DONE", "RETRY", "UNHANDLED"
EVENT_TYPE = re.compile(r"^[A-Za-z]{1,64}$")


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


class ProcessedMessageDB(Base):
    """Mensaje de la cola ya entregado a su manejador: una segunda entrega del mismo no repite el efecto."""
    __tablename__ = "processed_messages"

    message_id = Column(String(64), primary_key=True)
    processed_at = Column(DateTime(timezone=True), nullable=False)


class MessageQueue(Protocol):
    def send(self, body: str, event_type: str, message_id: str) -> None: ...


class ConsumerQueue(Protocol):
    def receive(self, max_messages: int, wait_seconds: int) -> List[dict]: ...

    def delete(self, receipt_handle: str) -> None: ...

    def retry_later(self, receipt_handle: str, seconds: int) -> None: ...


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

    def receive(self, max_messages: int, wait_seconds: int) -> List[dict]:
        response = self.client.receive_message(
            QueueUrl=self.url(), MaxNumberOfMessages=max_messages, WaitTimeSeconds=wait_seconds,
            MessageSystemAttributeNames=["ApproximateReceiveCount"],
        )
        return response.get("Messages", [])

    def delete(self, receipt_handle: str) -> None:
        self.client.delete_message(QueueUrl=self.url(), ReceiptHandle=receipt_handle)

    def retry_later(self, receipt_handle: str, seconds: int) -> None:
        self.client.change_message_visibility(QueueUrl=self.url(), ReceiptHandle=receipt_handle,
                                              VisibilityTimeout=seconds)


@lru_cache
def get_message_queue() -> SqsQueue:
    return SqsQueue(settings.SQS_QUEUE_EVENTS, settings.AWS_REGION, settings.AWS_ENDPOINT_URL)


def events_queue_configured() -> bool:
    """Sin la URL de la cola en AWS ni el emulador local no hay a quién leer."""
    return bool(settings.SQS_EVENTS_URL or settings.AWS_ENDPOINT_URL)


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


@dataclass(frozen=True)
class OutboxEvent:
    """Lo que recibe un manejador: el evento tal como quedó en el outbox de la base, no lo que dice la cola."""
    message_id: str
    event_type: str
    aggregate_id: str
    payload: Dict[str, Any]
    occurred_at: Optional[datetime] = None


EventHandler = Callable[[AsyncSession, OutboxEvent], Awaitable[None]]


def receive_count(message: dict) -> int:
    try:
        return max(int(message.get("Attributes", {}).get("ApproximateReceiveCount", 1)), 1)
    except (TypeError, ValueError):
        return 1


class OutboxConsumer:
    """Lee la cola de eventos y entrega cada mensaje a su manejador. Solo borra lo procesado: si algo falla, el mensaje
    vuelve tras una espera que crece con cada recepción y, tras ocho, SQS lo manda a la cola de mensajes fallidos."""

    def __init__(self, queue_factory: Callable[[], ConsumerQueue] = get_message_queue,
                 now: Callable[[], datetime] = lambda: datetime.now(timezone.utc),
                 retry_after_seconds: int = RETRY_AFTER_SECONDS, max_retry_seconds: int = MAX_RETRY_SECONDS,
                 ignored: frozenset = IGNORED_EVENT_TYPES):
        self.queue_factory = queue_factory
        self.now = now
        self.retry_after_seconds = retry_after_seconds
        self.max_retry_seconds = max_retry_seconds
        self.ignored = ignored
        self.handlers: Dict[str, EventHandler] = {}

    def register(self, event_type: str, handler: EventHandler) -> None:
        self.handlers[event_type] = handler

    def retry_delay(self, receives: int) -> int:
        """30 s, 60 s, 120 s… hasta 15 min: una caída larga no agota los intentos en minutos."""
        return min(self.retry_after_seconds * 2 ** (receives - 1), self.max_retry_seconds)

    async def consume(self, sessions: async_sessionmaker, wait_seconds: int = RECEIVE_WAIT_SECONDS) -> int:
        """Una vuelta de long polling; devuelve cuántos mensajes se borraron de la cola. Una falla con un mensaje
        no detiene el resto del lote."""
        queue = self.queue_factory()
        messages = await asyncio.to_thread(queue.receive, RECEIVE_MAX_MESSAGES, wait_seconds)
        done = 0
        for message in messages:
            receipt = message.get("ReceiptHandle", "")
            try:
                outcome = await self.process(sessions, message.get("Body", ""))
            except Exception as exc:
                logger.warning("No se pudo procesar un mensaje de la cola (%s); se reintentará", type(exc).__name__)
                outcome = RETRY
            if outcome == DONE:
                try:
                    await asyncio.to_thread(queue.delete, receipt)
                    done += 1
                except Exception as exc:
                    logger.warning("No se pudo borrar el evento procesado (%s); volverá y se omitirá",
                                   type(exc).__name__)
                continue
            seconds = self.max_retry_seconds if outcome == UNHANDLED else self.retry_delay(receive_count(message))
            try:
                await asyncio.to_thread(queue.retry_later, receipt, seconds)
            except Exception as exc:
                logger.warning("No se pudo aplazar el reintento del evento (%s)", type(exc).__name__)
        return done

    async def process(self, sessions: async_sessionmaker, body: str) -> str:
        """DONE si el mensaje ya no debe volver (procesado, repetido, ignorado o mal formado); RETRY si el manejador
        falló; UNHANDLED si nadie maneja su tipo. Una falla de la base o del commit sale como excepción."""
        try:
            data = json.loads(body)
            message_id, event_type = str(data["message_id"]), str(data["event_type"])
            uuid.UUID(message_id)
            if not EVENT_TYPE.match(event_type):
                raise ValueError
        except (ValueError, TypeError, KeyError):
            logger.warning("Mensaje mal formado en la cola de eventos: se descarta")
            return DONE
        handler = self.handlers.get(event_type)
        if handler is None:
            if event_type in self.ignored:
                return DONE
            logger.warning("Evento %s sin manejador: se reintentará hasta la DLQ", event_type)
            return UNHANDLED
        async with sessions() as session:
            stored = await session.get(OutboxMessageDB, message_id)
            if stored is None or stored.event_type != event_type:
                logger.warning("Evento %s que no está en el outbox: se descarta", event_type)
                return DONE
            event = OutboxEvent(message_id, event_type, stored.aggregate_id, dict(stored.payload or {}),
                                stored.created_at)
            # La fila bloquea a otra réplica con el mismo mensaje hasta el commit; si ya existía, el mensaje es repetido.
            claimed = await session.scalar(
                insert(ProcessedMessageDB).values(message_id=message_id, processed_at=self.now())
                .on_conflict_do_nothing(index_elements=[ProcessedMessageDB.message_id])
                .returning(ProcessedMessageDB.message_id)
            )
            if claimed is None:
                await session.rollback()
                return DONE
            try:
                await handler(session, event)
            except Exception as exc:
                await session.rollback()
                logger.warning("No se pudo procesar el evento %s (%s); se reintentará", event_type, type(exc).__name__)
                return RETRY
            await session.commit()
        return DONE


outbox_consumer = OutboxConsumer()
