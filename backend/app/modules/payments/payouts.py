"""Pagos de Solventa: indemnización a los beneficiarios y devolución de prima (HU-WEB-15, RC-29, RC-17).
Un pago por referencia, tipo y beneficiario; reintentar no vuelve a pagar lo aprobado (HA-11)."""
import uuid
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import List

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.modules.payments.gateway import SimulatedGateway
from app.modules.payments.models import RECEIPT_NUMBER_SEQ, PayoutDB


@dataclass
class Payee:
    name: str
    document: str
    percentage: float


def split(amount: float, payees: List[Payee]) -> List[float]:
    """Reparte en pesos enteros por porcentaje; el último beneficiario recibe el ajuste del redondeo."""
    shares = [round(amount * payee.percentage / 100) for payee in payees]
    if shares:
        shares[-1] += round(amount) - sum(shares)
    return shares


class PayoutService:
    def __init__(self, gateway):
        self.gateway = gateway

    async def create(
        self, session: AsyncSession, reference: str, kind: str, policy_number: str, amount: float, payees: List[Payee]
    ) -> List[PayoutDB]:
        """Crea los pagos que falten de esa referencia y ejecuta los pendientes; no hace commit."""
        existing = {p.beneficiary_document: p for p in await self.for_reference(session, reference, kind)}
        now = datetime.now(timezone.utc)
        for payee, share in zip(payees, split(amount, payees)):
            if payee.document not in existing:
                payout = PayoutDB(
                    payout_id=str(uuid.uuid4()), reference=reference, kind=kind, policy_number=policy_number,
                    beneficiary_name=payee.name, beneficiary_document=payee.document, percentage=payee.percentage,
                    amount=share, status="PENDING", created_at=now,
                )
                session.add(payout)
                existing[payee.document] = payout
        await session.flush()
        await self.execute(session, list(existing.values()))
        return list(existing.values())

    async def execute(self, session: AsyncSession, payouts: List[PayoutDB]) -> None:
        """Lleva a la pasarela los pagos que no están aprobados; cada intento usa su propia clave."""
        for payout in payouts:
            if payout.status == "APPROVED":
                continue
            attempt = f"{payout.payout_id}:{uuid.uuid4().hex[:8]}"
            reason = await self.gateway.payout(attempt, payout.amount, payout.beneficiary_document)
            if reason:
                payout.status, payout.decline_reason = "DECLINED", reason
            else:
                sequence = await session.scalar(select(RECEIPT_NUMBER_SEQ.next_value()))
                payout.status, payout.decline_reason = "APPROVED", None
                payout.receipt_number = f"REC-{payout.created_at.year}-{sequence:05d}"

    async def for_reference(self, session: AsyncSession, reference: str, kind: str) -> List[PayoutDB]:
        rows = await session.scalars(
            select(PayoutDB).where(PayoutDB.reference == reference, PayoutDB.kind == kind).order_by(PayoutDB.created_at)
        )
        return list(rows)


payout_service = PayoutService(SimulatedGateway())
