"""Push al teléfono del asegurado por Firebase Cloud Messaging (HU-MOV-11).
Sin credenciales de Firebase no se envía nada: la app igual avisa cuando sincroniza.
El mensaje no lleva datos sensibles: solo el radicado o la póliza y un texto general.
Los avisos salen de los eventos del outbox que entrega el consumidor de la cola, no de la petición (ADR 0030)."""
import asyncio
import json
import logging
import threading
import warnings
from datetime import datetime, timedelta, timezone
from functools import lru_cache
from typing import Callable, Dict, List, Literal, Optional, Protocol

from pydantic import BaseModel, ConfigDict, Field, StrictBool
from sqlalchemy import Boolean, Column, DateTime, String, delete, select, true
from sqlalchemy.dialects.postgresql import insert
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import settings
from app.core.database import Base
from app.core.outbox import EventHandler, OutboxEvent
from app.modules.claims.service import claim_service
from app.modules.policy.service import policy_service

logger = logging.getLogger("solventa.notifications")

CLAIM_MESSAGES = {
    "ASSIGNED": "Su siniestro {claim} está en evaluación.",
    "REJECTED": "Hay una decisión sobre su siniestro {claim}. Ábralo para ver el motivo.",
    "SETTLED": "Su siniestro {claim} fue aprobado.",
    "PAID": "El pago de su siniestro {claim} fue realizado.",
}
CLAIM_FILED_MESSAGE = "Recibimos su siniestro {claim}. Le avisaremos cuando haya novedades."
# Un «Recibimos su siniestro» que llega un día tarde (cola acumulada o reintentos) ya no informa nada.
CLAIM_FILED_MAX_AGE = timedelta(hours=24)


class PushDeliveryError(Exception):
    """Firebase o la base fallaron al enviar un aviso que se pidió sin mejor esfuerzo."""


class PushTokenDB(Base):
    """Token de Firebase del teléfono de un asegurado; el último registrado reemplaza al anterior."""
    __tablename__ = "push_tokens"

    token = Column(String(256), primary_key=True)
    document_number = Column(String(20), nullable=False, index=True)
    updated_at = Column(DateTime(timezone=True), nullable=False)


class PushPreferencesDB(Base):
    """Qué categorías de avisos acepta cada asegurado; sin fila, todas (mejora FE-MOV-04)."""
    __tablename__ = "push_preferences"

    document_number = Column(String(20), primary_key=True)
    claims = Column(Boolean, nullable=False, default=True, server_default=true())
    renewals = Column(Boolean, nullable=False, default=True, server_default=true())
    payments = Column(Boolean, nullable=False, default=True, server_default=true())
    updated_at = Column(DateTime(timezone=True), nullable=False)


class PushPreferences(BaseModel):
    """Las tres categorías van siempre: un cuerpo parcial no enciende en silencio las que el asegurado apagó."""
    model_config = ConfigDict(extra="forbid")

    claims: StrictBool = True
    renewals: StrictBool = True
    payments: StrictBool = True


class PushPreferencesRequest(PushPreferences):
    claims: StrictBool
    renewals: StrictBool
    payments: StrictBool


Category = Literal["claims", "renewals", "payments"]


class PushTokenRequest(BaseModel):
    token: str = Field(..., min_length=20, max_length=256, pattern=r"^[A-Za-z0-9_:\-]+$")


class PushSender(Protocol):
    def send(self, tokens: List[str], title: str, body: str, data: Dict[str, str]) -> Optional[List[str]]: ...


FIREBASE_HTTP_TIMEOUT_SECONDS = 10


def unregistered_tokens(tokens: List[str], responses: list) -> List[str]:
    """Revisa la respuesta de Firebase, que no lanza los fallos por token. Si ningún token recibió el aviso, registra
    las clases de error y lanza PushDeliveryError para que el consumidor lo reintente cuando alguno falló por algo
    pasajero o todos por la configuración del servidor (credencial o proyecto): así terminan en la DLQ y no se
    pierden. Un éxito parcial no se reintenta, para no duplicar. Devuelve los tokens que ya no sirven: no registrados,
    mal formados o de otro proyecto."""
    from firebase_admin import exceptions, messaging

    transient = (exceptions.UnavailableError, exceptions.InternalError, exceptions.UnknownError,
                 exceptions.DeadlineExceededError, exceptions.ResourceExhaustedError)
    # Incluye ThirdPartyAuthError y SenderIdMismatchError, que heredan de ellas.
    configuration = (exceptions.UnauthenticatedError, exceptions.PermissionDeniedError)
    stale = (messaging.UnregisteredError, exceptions.InvalidArgumentError, messaging.SenderIdMismatchError)

    def transient_failure(exc) -> bool:
        return isinstance(exc, transient) or (exc is not None and not isinstance(exc, exceptions.FirebaseError))

    if responses and not any(r.success for r in responses):
        logger.warning("Ningún teléfono recibió el push (%s)",
                       ", ".join(sorted({type(r.exception).__name__ for r in responses})))
        if (any(transient_failure(r.exception) for r in responses)
                or all(isinstance(r.exception, configuration) for r in responses)):
            raise PushDeliveryError()
    return [token for token, r in zip(tokens, responses) if isinstance(r.exception, stale)]


class FirebaseSender:
    def __init__(self, credentials_source):
        import firebase_admin
        from firebase_admin import credentials

        # Un tiempo corto por petición: un lote de la cola termina antes de que venza su visibilidad (QR-01).
        self.app = firebase_admin.initialize_app(
            credentials.Certificate(credentials_source), options={"httpTimeout": FIREBASE_HTTP_TIMEOUT_SECONDS},
            name="solventa",
        )

    def send(self, tokens: List[str], title: str, body: str, data: Dict[str, str]) -> List[str]:
        from firebase_admin import messaging

        payload = {**data, "title": title, "body": body}
        notification = messaging.Notification(title=title, body=body)
        android = messaging.AndroidConfig(priority="high")
        # La alternativa sin aviso de obsolescencia es `fid`, que exige que la app registre su ID de instalación en
        # lugar del token de FCM; mientras tanto se silencia solo ese aviso, como hace el SDK por dentro.
        with warnings.catch_warnings():
            warnings.filterwarnings("ignore", message="Message.token is deprecated", category=DeprecationWarning)
            messages = [messaging.Message(token=token, data=payload, notification=notification, android=android)
                        for token in tokens]
        return unregistered_tokens(tokens, messaging.send_each(messages, app=self.app).responses)


_sender_lock = threading.Lock()


def get_sender() -> Optional[PushSender]:
    """Un solo emisor por proceso: con el candado, dos hilos no inician Firebase a la vez (el segundo fallaría y
    quedaría en caché sin push)."""
    with _sender_lock:
        return _start_sender()


@lru_cache
def _start_sender() -> Optional[PushSender]:
    """En local, la ruta del archivo; en AWS, el contenido llega como secreto en la variable de entorno."""
    if not settings.FIREBASE_CREDENTIALS_FILE and not settings.FIREBASE_CREDENTIALS_JSON:
        return None
    try:
        return FirebaseSender(settings.FIREBASE_CREDENTIALS_FILE or json.loads(settings.FIREBASE_CREDENTIALS_JSON))
    except Exception as exc:  # credencial ausente o inválida: el resto del backend sigue
        logger.warning("Push desactivado: no se pudo iniciar Firebase (%s)", type(exc).__name__)
        return None


get_sender.cache_clear = _start_sender.cache_clear  # type: ignore[attr-defined]


class NotificationService:
    def __init__(self, sender_factory=get_sender, now: Callable[[], datetime] = lambda: datetime.now(timezone.utc)):
        self.sender_factory = sender_factory
        self.now = now

    async def register(self, session: AsyncSession, document_number: str, token: str) -> None:
        row = await session.get(PushTokenDB, token)
        if row is None:
            session.add(PushTokenDB(token=token, document_number=document_number, updated_at=datetime.now(timezone.utc)))
        else:
            row.document_number, row.updated_at = document_number, datetime.now(timezone.utc)
        await session.commit()

    async def preferences(self, session: AsyncSession, document_number: str) -> PushPreferences:
        row = await session.get(PushPreferencesDB, document_number)
        if row is None:
            return PushPreferences()
        return PushPreferences(claims=row.claims, renewals=row.renewals, payments=row.payments)

    async def save_preferences(
        self, session: AsyncSession, document_number: str, preferences: PushPreferences
    ) -> PushPreferences:
        values = {**preferences.model_dump(), "updated_at": datetime.now(timezone.utc)}
        # Dos guardados a la vez de una cuenta sin fila no chocan: el último gana.
        await session.execute(
            insert(PushPreferencesDB).values(document_number=document_number, **values)
            .on_conflict_do_update(index_elements=[PushPreferencesDB.document_number], set_=values)
        )
        await session.commit()
        logger.info("Preferencias de avisos actualizadas")
        return PushPreferences(**preferences.model_dump())

    async def notify(
        self, session: AsyncSession, document_number: str, title: str, body: str, data: Dict[str, str],
        category: Category = "claims", strict: bool = False,
    ) -> bool:
        """Mejor esfuerzo: un fallo de Firebase nunca rompe la operación que lo pidió.
        Con strict, el fallo sale como PushDeliveryError para que el consumidor de la cola lo reintente.
        Una categoría apagada por el asegurado o un teléfono sin token no se envían y devuelven False."""
        sender = self.sender_factory()
        if sender is None:
            return False
        try:
            # En un savepoint: si la lectura falla, la sesión de quien pidió el aviso sigue usable.
            async with session.begin_nested():
                if not getattr(await self.preferences(session, document_number), category):
                    return False
                tokens = list(await session.scalars(
                    select(PushTokenDB.token).where(PushTokenDB.document_number == document_number)
                ))
            if not tokens:
                return False
            stale = await asyncio.to_thread(sender.send, tokens, title, body, {**data, "category": category})
            if stale:
                await self.forget_tokens(session, stale)
            return True
        except Exception as exc:
            logger.warning("No se pudo enviar el push (%s)", type(exc).__name__)
            if strict:
                raise PushDeliveryError() from exc
            return False

    async def forget_tokens(self, session: AsyncSession, tokens: List[str]) -> None:
        """Tokens que Firebase ya no acepta (app desinstalada, mal formados o de otro proyecto): no se vuelven a
        intentar. El aviso ya salió, así que una falla aquí solo se registra."""
        try:
            async with session.begin_nested():
                await session.execute(delete(PushTokenDB).where(PushTokenDB.token.in_(tokens)))
        except Exception as exc:
            logger.warning("No se pudieron borrar los tokens que Firebase rechazó (%s)", type(exc).__name__)

    async def claim_changed(
        self, session: AsyncSession, document_number: str, claim_number: str, kind: str, strict: bool = False
    ) -> bool:
        if kind not in CLAIM_MESSAGES:
            return False
        return await self.notify(
            session, document_number, "Solventa · Su siniestro", CLAIM_MESSAGES[kind].format(claim=claim_number),
            {"type": "CLAIM_STATUS", "claim_number": claim_number},
            category="payments" if kind == "PAID" else "claims", strict=strict,
        )

    async def renewal_due(
        self, session: AsyncSession, document_number: str, policy_number: str, valid_until, strict: bool = False
    ) -> bool:
        return await self.notify(
            session, document_number, "Solventa · Su póliza vence pronto",
            f"Su póliza {policy_number} vence el {valid_until:%d/%m/%Y}. Puede renovarla desde la app.",
            {"type": "RENEWAL_REMINDER", "policy_number": policy_number},
            category="renewals", strict=strict,
        )

    # -- Manejadores de los eventos del outbox (ADR 0030) ----------------------------------------

    async def holder_of(self, session: AsyncSession, policy_number: Optional[str]):
        if not policy_number:
            return None
        return await policy_service.find(session, policy_number)

    async def on_claim_filed(self, session: AsyncSession, event: OutboxEvent) -> None:
        """Solo mientras el siniestro sigue recibido y el evento es del último día: un mensaje acumulado no avisa
        de algo que ya cambió."""
        if event.occurred_at is not None and self.now() - event.occurred_at > CLAIM_FILED_MAX_AGE:
            return
        if await claim_service.status_of(session, event.aggregate_id) != "REPORTED":
            return
        policy = await self.holder_of(session, event.payload.get("policy_number"))
        if policy is None:
            return
        await self.notify(
            session, policy.insured_document, "Solventa · Su siniestro",
            CLAIM_FILED_MESSAGE.format(claim=event.aggregate_id),
            {"type": "CLAIM_STATUS", "claim_number": event.aggregate_id}, category="claims", strict=True,
        )

    async def on_claim_status_changed(self, session: AsyncSession, event: OutboxEvent) -> None:
        policy = await self.holder_of(session, event.payload.get("policy_number"))
        if policy is None:
            return
        await self.claim_changed(session, policy.insured_document, event.aggregate_id,
                                 str(event.payload.get("kind")), strict=True)

    async def on_renewal_due(self, session: AsyncSession, event: OutboxEvent) -> None:
        """Si la póliza ya no está vigente o cambió de versión (cancelada, renovada o endosada en el intermedio), el
        aviso ya no aplica."""
        policy = await self.holder_of(session, event.aggregate_id)
        if policy is None or policy.status != "ACTIVE" or event.payload.get("version") != (policy.version or 1):
            return
        await self.renewal_due(session, policy.insured_document, policy.policy_number, policy.valid_until, strict=True)

    def event_handlers(self) -> Dict[str, EventHandler]:
        return {
            "ClaimFiled": self.on_claim_filed,
            "ClaimStatusChanged": self.on_claim_status_changed,
            "RenewalDue": self.on_renewal_due,
        }


notification_service = NotificationService()
