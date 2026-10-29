"""Detalle de una póliza para operaciones, con alcance por socio y titular enmascarado (HU-WEB-09)."""
import re
from typing import Optional

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.modules.claims.models import ClaimDB, ClaimSummary
from app.modules.payments.models import PaymentTransactionDB
from app.modules.policy.models import (
    PolicyDB,
    PolicyDetail,
    PolicyDetailCoverage,
    PolicyDetailHolder,
    PolicyPayment,
)
from app.modules.policy.search import mask_document
from app.modules.policy.service import included_coverages, insured_amount
from app.modules.rating.models import PartnerDB


def mask_email(email: str) -> str:
    """Primera letra y dominio; los asteriscos no revelan el largo."""
    local, _, domain = email.partition("@")
    return f"{local[:1]}*****@{domain}"


def mask_phone(phone: str) -> str:
    return mask_document(re.sub(r"\D", "", phone))


async def get_policy_detail(
    session: AsyncSession, policy_number: str, partner_id: Optional[str]
) -> Optional[PolicyDetail]:
    """None si no existe o si es de otro socio: el llamador no distingue los dos casos."""
    stmt = (
        select(PolicyDB, PartnerDB.name)
        .outerjoin(PartnerDB, PartnerDB.partner_id == PolicyDB.partner_id)
        .where(PolicyDB.policy_number == policy_number)
    )
    if partner_id is not None:
        stmt = stmt.where(PolicyDB.partner_id == partner_id)
    row = (await session.execute(stmt)).first()
    if row is None:
        return None
    policy, partner_name = row

    payments = await session.scalars(
        select(PaymentTransactionDB)
        .where(PaymentTransactionDB.policy_number == policy_number)
        .order_by(PaymentTransactionDB.created_at.desc(), PaymentTransactionDB.transaction_id.desc())
    )
    claims = await session.scalars(
        select(ClaimDB).where(ClaimDB.policy_number == policy_number).order_by(ClaimDB.reported_at.desc())
    )
    included = included_coverages(policy.conditions)
    return PolicyDetail(
        policy_number=policy.policy_number,
        status=policy.status,
        plan=policy.conditions["tier"],
        valid_from=policy.issued_at,
        valid_until=policy.valid_until,
        partner_name=partner_name,
        holder=PolicyDetailHolder(
            name=policy.holder_name,
            document_type=policy.holder_document_type,
            document=mask_document(policy.insured_document),
            email=mask_email(policy.holder_email) if policy.holder_email else None,
            phone=mask_phone(policy.holder_phone) if policy.holder_phone else None,
        ),
        insured_amount=insured_amount(included),
        monthly_premium=policy.conditions["monthly_premium"],
        annual_premium=policy.conditions["annual_premium"],
        coverages=[
            PolicyDetailCoverage(name=c["name"], limit=c["limit"], deductible=c.get("deductible", 0.0)) for c in included
        ],
        payments=[
            PolicyPayment(
                paid_at=p.created_at,
                amount=p.amount,
                currency=p.currency,
                method=p.payment_method,
                status=p.status,
                receipt_number=p.receipt_number,
                decline_reason=p.decline_reason,
            )
            for p in payments
        ],
        claims=[
            ClaimSummary(claim_number=c.claim_number, status=c.status, reported_at=c.reported_at) for c in claims
        ],
    )
