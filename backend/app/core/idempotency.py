"""Idempotencia por clave: la misma petición repite la respuesta guardada sin volver a ejecutarse (ASR-05, HA-11)."""
from datetime import datetime, timedelta, timezone
import hashlib
from typing import AbstractSet, Optional

from pydantic import BaseModel
from sqlalchemy import Column, DateTime, Integer, String, delete, func
from sqlalchemy.dialects.postgresql import JSONB
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import Base


class IdempotencyConflictError(Exception):
    pass


class IdempotencyRecordDB(Base):
    """Respuesta de una operación ya ejecutada, por socio y clave (S8-Arq §4.2)."""
    __tablename__ = "idempotency_records"

    partner_id = Column(String(64), primary_key=True)
    idempotency_key = Column(String(128), primary_key=True)
    request_hash = Column(String(64), nullable=False)
    response_status = Column(Integer, nullable=False)
    response_body = Column(JSONB, nullable=False)
    created_at = Column(DateTime(timezone=True), server_default=func.now(), nullable=False)


def request_hash(request: BaseModel, exclude: Optional[AbstractSet[str]] = None) -> str:
    """Huella del cuerpo; `exclude` deja fuera campos que no deben poder recuperarse de ella, como una ubicación."""
    return hashlib.sha256(request.model_dump_json(exclude=exclude).encode()).hexdigest()


async def find_response(
    session: AsyncSession, partner_id: str, idempotency_key: str, request: BaseModel, max_age_hours: int = 24,
    exclude: Optional[AbstractSet[str]] = None,
) -> Optional[IdempotencyRecordDB]:
    """Respuesta guardada para la clave, o None si es nueva; si la clave se usó con otra petición, lanza el conflicto.
    Si la clave tiene más de max_age_hours (24h), expira y se elimina para permitir una nueva operación (ASR-05, HA-11)."""
    record = await session.get(IdempotencyRecordDB, (partner_id, idempotency_key))
    if record:
        now = datetime.now(timezone.utc)
        if record.created_at:
            record_created = (
                record.created_at
                if record.created_at.tzinfo
                else record.created_at.replace(tzinfo=timezone.utc)
            )
            if now - record_created > timedelta(hours=max_age_hours):
                await session.delete(record)
                await session.flush()
                return None
        if record.request_hash != request_hash(request, exclude):
            raise IdempotencyConflictError()
    return record


def save_response(
    session: AsyncSession, partner_id: str, idempotency_key: str, request: BaseModel, status: int, body: dict,
    exclude: Optional[AbstractSet[str]] = None,
) -> IdempotencyRecordDB:
    """Agrega la respuesta a la transacción en curso, para que se confirme junto con el efecto de la operación."""
    record = IdempotencyRecordDB(
        partner_id=partner_id,
        idempotency_key=idempotency_key,
        request_hash=request_hash(request, exclude),
        response_status=status,
        response_body=body,
        created_at=datetime.now(timezone.utc),
    )
    session.add(record)
    return record


async def purge_expired(session: AsyncSession, max_age_hours: int = 24) -> int:
    """Borra las respuestas que ya vencieron; `find_response` no las repetiría y solo guardarían datos de más."""
    cutoff = datetime.now(timezone.utc) - timedelta(hours=max_age_hours)
    result = await session.execute(delete(IdempotencyRecordDB).where(IdempotencyRecordDB.created_at < cutoff))
    await session.commit()
    return result.rowcount
