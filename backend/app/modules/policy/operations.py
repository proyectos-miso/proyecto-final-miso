"""Operaciones del analista sobre una póliza: endosos, cancelación, terminación y auditoría (HU-WEB-15).
Cada una queda en `policy_events`, que no se puede cambiar ni borrar (RC-17, RC-19, RC-21)."""
import calendar
from datetime import datetime, timedelta, timezone
from typing import Callable, List

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.modules.claims.models import ClaimDB, ClaimEventDB
from app.modules.payments.models import PaymentTransactionDB, PayoutDB
from app.modules.payments.payouts import Payee, payout_service
from app.modules.policy.models import (
    AuditEntry,
    CancellationRequest,
    EndorsementRequest,
    PolicyAudit,
    PolicyDB,
    PolicyEventDB,
    PolicyOperationResult,
)
from app.modules.policy.search import mask_document
from app.modules.policy.service import included_coverages, insured_amount, one_year_after

CLOSED = ("CANCELLED", "TERMINATED")
RENEWAL_WINDOW = timedelta(days=30)
AUTOMATIC = "Proceso automático"


class NotRenewableError(Exception):
    """Faltan más de 30 días para el vencimiento, o la póliza está cancelada o terminada."""


class PolicyClosedError(Exception):
    """La póliza está cancelada o terminada: no admite más operaciones."""


class InvalidEndorsementError(Exception):
    pass


def scaled_coverages(conditions: dict, new_amount: float) -> List[dict]:
    """Baja todas las coberturas en la misma proporción que la suma asegurada; la prima no cambia."""
    factor = new_amount / insured_amount(included_coverages(conditions))
    return [{**c, "limit": round(c["limit"] * factor, 2)} for c in conditions["coverages"]]


def months_between(start: datetime, end: datetime) -> int:
    months = (end.year - start.year) * 12 + end.month - start.month
    return months - 1 if end.day < start.day else months


def add_months(moment: datetime, months: int) -> datetime:
    month = moment.month - 1 + months
    year, month = moment.year + month // 12, month % 12 + 1
    return moment.replace(year=year, month=month, day=min(moment.day, calendar.monthrange(year, month)[1]))


def prorated_refund(policy: PolicyDB, now: datetime) -> float:
    """Prima mensual de los días que faltan del mes pagado; una póliza sin pagar no devuelve nada."""
    if policy.status != "ACTIVE":
        return 0.0
    start = add_months(policy.issued_at, months_between(policy.issued_at, now))
    end = add_months(start, 1)
    remaining = max((end - now).total_seconds(), 0) / (end - start).total_seconds()
    return round(policy.conditions["monthly_premium"] * remaining)


class PolicyOperations:
    def __init__(self, now: Callable[[], datetime] = lambda: datetime.now(timezone.utc)):
        self.now = now

    async def lock_open(self, session: AsyncSession, policy_number: str) -> PolicyDB:
        policy = await session.scalar(
            select(PolicyDB).where(PolicyDB.policy_number == policy_number).with_for_update()
        )
        if policy.status in CLOSED:
            raise PolicyClosedError()
        return policy

    async def endorse(
        self, session: AsyncSession, policy_number: str, request: EndorsementRequest, actor: str
    ) -> PolicyOperationResult:
        """Cambia beneficiarios o baja la suma asegurada; guarda el antes y el después y pide carátula nueva."""
        policy = await self.lock_open(session, policy_number)
        if request.kind == "BENEFICIARIES":
            if not request.beneficiaries:
                raise InvalidEndorsementError("Registre los beneficiarios del endoso.")
            before = {"beneficiaries": policy.beneficiaries}
            after = {"beneficiaries": [b.model_dump() for b in request.beneficiaries]}
            policy.beneficiaries = after["beneficiaries"]
        else:
            current = insured_amount(included_coverages(policy.conditions))
            if request.insured_amount is None or request.insured_amount >= current:
                raise InvalidEndorsementError("La nueva suma asegurada debe ser menor que la actual.")
            before = {"insured_amount": current}
            after = {"insured_amount": request.insured_amount}
            policy.conditions = {**policy.conditions, "coverages": scaled_coverages(policy.conditions, request.insured_amount)}
        policy.version = (policy.version or 1) + 1
        policy.pdf_s3_key = None
        session.add(PolicyEventDB(
            policy_number=policy_number, kind=f"ENDORSEMENT_{request.kind}", version=policy.version, actor=actor,
            justification=request.justification, before=before, after=after, created_at=self.now(),
        ))
        await session.commit()
        return PolicyOperationResult(policy_number=policy_number, status=policy.status, version=policy.version)

    async def cancel(
        self, session: AsyncSession, policy_number: str, request: CancellationRequest, actor: str
    ) -> PolicyOperationResult:
        """Cancela a pedido del cliente y devuelve al titular la prima proporcional del mes pagado."""
        policy = await self.lock_open(session, policy_number)
        now = self.now()
        refund = prorated_refund(policy, now)
        policy.status = "CANCELLED"
        session.add(PolicyEventDB(
            policy_number=policy_number, kind="CANCELLED", version=policy.version or 1, actor=actor,
            justification=request.reason, amount=refund, created_at=now,
        ))
        if refund:
            holder = Payee(policy.holder_name or "Titular de la póliza", policy.insured_document, 100)
            await payout_service.create(session, policy_number, "REFUND", policy_number, refund, [holder])
        await session.commit()
        return PolicyOperationResult(
            policy_number=policy_number, status=policy.status, version=policy.version or 1, refund_amount=refund
        )

    async def renew(self, session: AsyncSession, policy_number: str, actor: str) -> PolicyOperationResult:
        """Un año más desde el fin de la vigencia actual, con la misma prima (HU-MOV-11, RC-24).
        Si ya había vencido, el periodo nuevo queda pendiente de pago hasta que el asesor lo cobre."""
        policy = await session.scalar(
            select(PolicyDB).where(PolicyDB.policy_number == policy_number).with_for_update()
        )
        now = self.now()
        if policy.status in CLOSED or policy.valid_until - now > RENEWAL_WINDOW:
            raise NotRenewableError()
        before = {"valid_until": policy.valid_until.isoformat(), "status": policy.status}
        policy.valid_until = one_year_after(policy.valid_until)
        if policy.status == "EXPIRED":
            policy.status = "ISSUED"
        policy.version = (policy.version or 1) + 1
        policy.pdf_s3_key = None
        session.add(PolicyEventDB(
            policy_number=policy_number, kind="RENEWED", version=policy.version, actor=actor,
            justification="Renovación por un año con la prima vigente.", before=before,
            after={"valid_until": policy.valid_until.isoformat(), "status": policy.status}, created_at=now,
        ))
        await session.commit()
        return PolicyOperationResult(
            policy_number=policy_number, status=policy.status, version=policy.version, valid_until=policy.valid_until
        )

    async def expire_due(self, session: AsyncSession) -> int:
        """Marca vencidas las pólizas cuya vigencia ya terminó (tarea diaria)."""
        now = self.now()
        due = list(await session.scalars(
            select(PolicyDB).where(PolicyDB.status.in_(("ACTIVE", "ISSUED")), PolicyDB.valid_until <= now)
            .with_for_update(skip_locked=True)
        ))
        for policy in due:
            policy.status = "EXPIRED"
            session.add(PolicyEventDB(
                policy_number=policy.policy_number, kind="EXPIRED", version=policy.version or 1, actor=AUTOMATIC,
                justification="Terminó la vigencia sin renovación.", created_at=now,
            ))
        await session.commit()
        return len(due)

    async def remind_renewals(self, session: AsyncSession, notify) -> int:
        """Avisa una sola vez por versión a las pólizas vigentes que vencen en 30 días o menos (RC-27)."""
        now = self.now()
        reminded = select(PolicyEventDB.policy_number).where(
            PolicyEventDB.kind == "RENEWAL_REMINDER", PolicyEventDB.version == PolicyDB.version
        ).where(PolicyEventDB.policy_number == PolicyDB.policy_number).exists()
        due = list(await session.scalars(
            select(PolicyDB).where(
                PolicyDB.status == "ACTIVE", PolicyDB.valid_until > now, PolicyDB.valid_until <= now + RENEWAL_WINDOW,
                ~reminded,
            )
        ))
        for policy in due:
            session.add(PolicyEventDB(
                policy_number=policy.policy_number, kind="RENEWAL_REMINDER", version=policy.version or 1,
                actor=AUTOMATIC, justification=f"Vence el {policy.valid_until:%d/%m/%Y}.", created_at=now,
            ))
        await session.commit()
        for policy in due:
            await notify(session, policy.insured_document, policy.policy_number, policy.valid_until)
        return len(due)

    def terminate(self, session: AsyncSession, policy: PolicyDB, claim_number: str, actor: str) -> None:
        """Un siniestro de muerte liquidado termina la póliza; no hace commit (RC-17)."""
        if policy.status in CLOSED:
            return
        policy.status = "TERMINATED"
        session.add(PolicyEventDB(
            policy_number=policy.policy_number, kind="TERMINATED", version=policy.version or 1, actor=actor,
            justification=f"Siniestro de muerte {claim_number} liquidado.", created_at=self.now(),
        ))

    async def audit(self, session: AsyncSession, policy: PolicyDB) -> PolicyAudit:
        """Emisión, cobros, endosos, cancelación, siniestros y pagos de la póliza, en orden (RC-19)."""
        entries = [AuditEntry(occurred_at=policy.issued_at, kind="ISSUED", actor="Emisión", summary="Póliza emitida")]
        for charge in await session.scalars(
            select(PaymentTransactionDB).where(PaymentTransactionDB.policy_number == policy.policy_number)
        ):
            entries.append(AuditEntry(
                occurred_at=charge.created_at, kind=f"PREMIUM_{charge.status}", actor="Pasarela de pagos",
                summary="Cobro de prima aprobado" if charge.status == "APPROVED" else "Cobro de prima rechazado",
                amount=charge.amount, reference=charge.receipt_number,
            ))
        for event in await session.scalars(
            select(PolicyEventDB).where(PolicyEventDB.policy_number == policy.policy_number)
        ):
            entries.append(AuditEntry(
                occurred_at=event.created_at, kind=event.kind, actor=event.actor, summary=POLICY_EVENTS[event.kind],
                justification=event.justification, amount=event.amount, reference=f"Versión {event.version}",
            ))
        claim_numbers = select(ClaimDB.claim_number).where(ClaimDB.policy_number == policy.policy_number)
        for event in await session.scalars(select(ClaimEventDB).where(ClaimEventDB.claim_number.in_(claim_numbers))):
            entries.append(AuditEntry(
                occurred_at=event.created_at, kind=f"CLAIM_{event.kind}", actor=event.actor,
                summary=CLAIM_EVENTS.get(event.kind, event.kind), justification=event.justification,
                amount=event.amount, reference=event.claim_number,
            ))
        for payout in await session.scalars(select(PayoutDB).where(PayoutDB.policy_number == policy.policy_number)):
            entries.append(AuditEntry(
                occurred_at=payout.created_at, kind=f"PAYOUT_{payout.status}", actor="Pasarela de pagos",
                summary=f"{'Indemnización' if payout.kind == 'INDEMNITY' else 'Devolución de prima'} a "
                        f"{payout.beneficiary_name} ({mask_document(payout.beneficiary_document)})",
                amount=payout.amount, reference=payout.receipt_number or payout.reference,
            ))
        entries.sort(key=lambda entry: entry.occurred_at)
        return PolicyAudit(policy_number=policy.policy_number, entries=entries)


POLICY_EVENTS = {
    "ENDORSEMENT_BENEFICIARIES": "Endoso de beneficiarios",
    "ENDORSEMENT_SUM_INSURED": "Endoso de suma asegurada",
    "CANCELLED": "Póliza cancelada",
    "TERMINATED": "Póliza terminada",
    "RENEWED": "Póliza renovada",
    "EXPIRED": "Póliza vencida",
    "RENEWAL_REMINDER": "Aviso de vencimiento enviado",
}
CLAIM_EVENTS = {
    "REPORTED": "Siniestro reportado",
    "ASSIGNED": "Siniestro asignado",
    "EXPERT_ASSIGNED": "Perito asignado",
    "REJECTED": "Siniestro rechazado",
    "SETTLED": "Siniestro liquidado",
    "PAID": "Indemnización pagada",
}

policy_operations = PolicyOperations()
