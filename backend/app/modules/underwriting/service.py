"""Registro de los rechazos de suscripción; solo inserta (HU-WEB-07, HA-17)."""
import uuid
from datetime import datetime

from sqlalchemy.ext.asyncio import AsyncSession

from app.modules.underwriting.models import UnderwritingDecision, UnderwritingRejectionDB


def new_rejection_id() -> str:
    return f"UWR-{uuid.uuid4().hex[:12].upper()}"


async def record_rejection(
    session: AsyncSession,
    rejection_id: str,
    document_number: str,
    requested_amount: float,
    decision: UnderwritingDecision,
    created_at: datetime,
) -> None:
    session.add(
        UnderwritingRejectionDB(
            rejection_id=rejection_id,
            document_number=document_number,
            rule_id=decision.rule_id,
            reason=decision.reason,
            requested_amount=requested_amount,
            max_approvable_amount=decision.max_approvable_amount,
            created_at=created_at,
        )
    )
    await session.commit()
