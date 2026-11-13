"""Push al teléfono del asegurado por Firebase Cloud Messaging (HU-MOV-11).
Sin credenciales de Firebase no se envía nada: la app igual avisa cuando sincroniza.
El mensaje no lleva datos sensibles: solo el radicado o la póliza y un texto general."""
import asyncio
import json
import logging
from datetime import datetime, timezone
from functools import lru_cache
from typing import Dict, List, Optional, Protocol

from pydantic import BaseModel, Field
from sqlalchemy import Column, DateTime, String, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import settings
from app.core.database import Base

logger = logging.getLogger("solventa.notifications")

CLAIM_MESSAGES = {
    "ASSIGNED": "Su siniestro {claim} está en evaluación.",
    "REJECTED": "Hay una decisión sobre su siniestro {claim}. Ábralo para ver el motivo.",
    "SETTLED": "Su siniestro {claim} fue aprobado.",
    "PAID": "El pago de su siniestro {claim} fue realizado.",
}


class PushTokenDB(Base):
    """Token de Firebase del teléfono de un asegurado; el último registrado reemplaza al anterior."""
    __tablename__ = "push_tokens"

    token = Column(String(256), primary_key=True)
    document_number = Column(String(20), nullable=False, index=True)
    updated_at = Column(DateTime(timezone=True), nullable=False)


class PushTokenRequest(BaseModel):
    token: str = Field(..., min_length=20, max_length=256, pattern=r"^[A-Za-z0-9_:\-]+$")


class PushSender(Protocol):
    def send(self, tokens: List[str], title: str, body: str, data: Dict[str, str]) -> None: ...


class FirebaseSender:
    def __init__(self, credentials_source):
        import firebase_admin
        from firebase_admin import credentials

        self.app = firebase_admin.initialize_app(credentials.Certificate(credentials_source), name="solventa")

    def send(self, tokens: List[str], title: str, body: str, data: Dict[str, str]) -> None:
        from firebase_admin import messaging

        message = messaging.MulticastMessage(
            tokens=tokens, data={**data, "title": title, "body": body},
            notification=messaging.Notification(title=title, body=body),
            android=messaging.AndroidConfig(priority="high"),
        )
        messaging.send_each_for_multicast(message, app=self.app)


@lru_cache
def get_sender() -> Optional[PushSender]:
    """En local, la ruta del archivo; en AWS, el contenido llega como secreto en la variable de entorno."""
    if not settings.FIREBASE_CREDENTIALS_FILE and not settings.FIREBASE_CREDENTIALS_JSON:
        return None
    try:
        return FirebaseSender(settings.FIREBASE_CREDENTIALS_FILE or json.loads(settings.FIREBASE_CREDENTIALS_JSON))
    except Exception as exc:  # credencial ausente o inválida: el resto del backend sigue
        logger.warning("Push desactivado: no se pudo iniciar Firebase (%s)", type(exc).__name__)
        return None


class NotificationService:
    def __init__(self, sender_factory=get_sender):
        self.sender_factory = sender_factory

    async def register(self, session: AsyncSession, document_number: str, token: str) -> None:
        row = await session.get(PushTokenDB, token)
        if row is None:
            session.add(PushTokenDB(token=token, document_number=document_number, updated_at=datetime.now(timezone.utc)))
        else:
            row.document_number, row.updated_at = document_number, datetime.now(timezone.utc)
        await session.commit()

    async def notify(
        self, session: AsyncSession, document_number: str, title: str, body: str, data: Dict[str, str]
    ) -> bool:
        """Mejor esfuerzo: un fallo de Firebase nunca rompe la operación que lo pidió."""
        sender = self.sender_factory()
        if sender is None:
            return False
        tokens = list(await session.scalars(
            select(PushTokenDB.token).where(PushTokenDB.document_number == document_number)
        ))
        if not tokens:
            return False
        try:
            await asyncio.to_thread(sender.send, tokens, title, body, data)
            return True
        except Exception as exc:
            logger.warning("No se pudo enviar el push (%s)", type(exc).__name__)
            return False

    async def claim_changed(self, session: AsyncSession, document_number: str, claim_number: str, kind: str) -> bool:
        if kind not in CLAIM_MESSAGES:
            return False
        return await self.notify(
            session, document_number, "Solventa · Su siniestro", CLAIM_MESSAGES[kind].format(claim=claim_number),
            {"type": "CLAIM_STATUS", "claim_number": claim_number},
        )

    async def renewal_due(self, session: AsyncSession, document_number: str, policy_number: str, valid_until) -> bool:
        return await self.notify(
            session, document_number, "Solventa · Su póliza vence pronto",
            f"Su póliza {policy_number} vence el {valid_until:%d/%m/%Y}. Puede renovarla desde la app.",
            {"type": "RENEWAL_REMINDER", "policy_number": policy_number},
        )


notification_service = NotificationService()
