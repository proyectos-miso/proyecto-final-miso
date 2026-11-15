"""Auditoría de suscripción: detalle de una decisión y recálculo con las mismas funciones puras (mejora FE-WEB-08)."""
import json
import logging
from datetime import date, datetime, timezone
from typing import Any, List, Literal, Optional, Union

from pydantic import BaseModel
from sqlalchemy.ext.asyncio import AsyncSession

from app.modules.profile.service import profile_service
from app.modules.rating import service as rating
from app.modules.rating.models import PlanTier, PremiumFactor, QuoteDB
from app.modules.rating.partners import operator_ref
from app.modules.underwriting import rules
from app.modules.underwriting.models import UnderwritingRejectionDB

audit_logger = logging.getLogger("solventa.audit")

QUOTE_PREFIX = "QUO-"
REJECTION_NOT_REPLAYABLE = (
    "Los rechazos no guardan el puntaje del buró (HU-WEB-07), así que no se pueden recalcular."
)


class DecisionNotFoundError(Exception):
    pass


class DecisionNotReplayableError(Exception):
    pass


class RejectionNotReplayableError(Exception):
    pass


class DecisionInputs(BaseModel):
    document_masked: Optional[str]
    birth_date: Optional[date]
    insured_amount: Optional[float]
    occupation_risk: Optional[int]
    score: Optional[float]


class RejectionInputs(BaseModel):
    """Lo que guarda un rechazo: sin puntaje del buró ni fecha de nacimiento (CTL-03 de HU-WEB-07)."""
    document_masked: Optional[str]
    insured_amount: float


class TierOutcome(BaseModel):
    tier: str
    monthly_premium: float
    breakdown: List[PremiumFactor]


class DecisionOutcome(BaseModel):
    status: str
    degradation_reason: Optional[str] = None
    tiers: List[TierOutcome] = []
    rule_id: Optional[str] = None
    reason: Optional[str] = None
    max_approvable_amount: Optional[float] = None


class DecisionDetail(BaseModel):
    decision_id: str
    kind: Literal["quote", "rejection"]
    created_at: datetime
    rules_version: Optional[str]
    consent_revoked: bool
    replayable: bool
    inputs: Union[DecisionInputs, RejectionInputs]
    outcome: DecisionOutcome


class Difference(BaseModel):
    field: str
    then: Any
    now: Any


class ReplayResult(BaseModel):
    decision_id: str
    matches: bool
    differences: List[Difference]
    rules_version_then: str
    rules_version_now: str


class ConsentRevocation(BaseModel):
    document_masked: str
    revoked: int


def mask_document(document_number: str) -> str:
    """Solo los cuatro últimos dígitos."""
    if len(document_number) <= 4:
        return "****"
    return "*" * (len(document_number) - 4) + document_number[-4:]


def log_audit_event(event_code: str, operator: str, **fields: Any) -> None:
    """Evento de auditoría sin datos personales: el operador va como `operator_ref`."""
    payload = {
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "event": "underwriting_audit",
        "event_code": event_code,
        "operator_ref": operator_ref(operator),
        **fields,
    }
    audit_logger.info(json.dumps(payload, ensure_ascii=False))


def _day(value: Optional[datetime]) -> Optional[date]:
    if value is None:
        return None
    return value.date() if isinstance(value, datetime) else value


def _age_then(birth_date: date, created_at: datetime) -> int:
    """Edad a la fecha de la decisión, no a la de hoy."""
    return rating.age_on(birth_date, created_at.date())


async def _load(session: AsyncSession, decision_id: str):
    model = QuoteDB if decision_id.startswith(QUOTE_PREFIX) else UnderwritingRejectionDB
    row = await session.get(model, decision_id)
    if row is None:
        raise DecisionNotFoundError()
    return row


def _quote_replayable(quote: QuoteDB) -> bool:
    return None not in (quote.rules_version, quote.insured_amount, quote.occupation_risk, quote.birth_date)


async def detail(session: AsyncSession, decision_id: str, operator: str) -> DecisionDetail:
    row = await _load(session, decision_id)
    is_quote = isinstance(row, QuoteDB)
    revoked = await profile_service.consent_revoked_async(
        session, row.document_number, row.consent_id if is_quote else None
    )
    log_audit_event("DECISION_VIEWED", operator, decision_id=decision_id, consent_revoked=revoked)
    if is_quote:
        inputs = DecisionInputs(
            document_masked=None if revoked else mask_document(row.document_number),
            birth_date=None if revoked else _day(row.birth_date),
            insured_amount=row.insured_amount,
            occupation_risk=row.occupation_risk,
            score=None if revoked else row.score_applied,
        )
        outcome = DecisionOutcome(
            status=row.status,
            degradation_reason=row.degradation_reason,
            tiers=[
                TierOutcome(tier=tier["tier"], monthly_premium=tier["monthly_premium"], breakdown=tier.get("breakdown", []))
                for tier in row.tiers
            ],
        )
        replayable = _quote_replayable(row)
    else:
        inputs = RejectionInputs(
            document_masked=None if revoked else mask_document(row.document_number),
            insured_amount=row.requested_amount,
        )
        outcome = DecisionOutcome(
            status="review_required",
            rule_id=row.rule_id,
            reason=row.reason,
            max_approvable_amount=row.max_approvable_amount,
        )
        replayable = False
    return DecisionDetail(
        decision_id=decision_id,
        kind="quote" if is_quote else "rejection",
        created_at=row.created_at,
        rules_version=row.rules_version,
        consent_revoked=revoked,
        replayable=replayable,
        inputs=inputs,
        outcome=outcome,
    )


def _replay_quote(quote: QuoteDB) -> List[Difference]:
    age = _age_then(_day(quote.birth_date), quote.created_at)
    score = quote.score_applied
    differences: List[Difference] = []
    decision = rules.evaluate(quote.insured_amount, age, score)
    if decision is not None:
        differences.append(Difference(field="decision", then="approved", now=f"review_required:{decision.rule_id}"))
        return differences
    for stored in quote.tiers:
        tier = PlanTier(stored["tier"])
        breakdown = rating.build_breakdown(
            quote.insured_amount, age, quote.occupation_risk, score, tier, partial_profile=quote.status == "degraded"
        )
        monthly = float(sum(factor.amount for factor in breakdown))
        if monthly != float(stored["monthly_premium"]):
            differences.append(Difference(field=f"{tier.value}.monthly_premium", then=stored["monthly_premium"], now=monthly))
        then_factors = {factor["code"]: factor for factor in stored.get("breakdown", [])}
        now_factors = {factor.code: factor for factor in breakdown}
        for code in sorted(set(then_factors) | set(now_factors)):
            before, after = then_factors.get(code), now_factors.get(code)
            for attribute in ("multiplier", "amount"):
                old = None if before is None else float(before[attribute])
                new = None if after is None else float(getattr(after, attribute))
                if old != new:
                    differences.append(Difference(field=f"{tier.value}.{code}.{attribute}", then=old, now=new))
    return differences


async def replay(session: AsyncSession, decision_id: str, operator: str) -> ReplayResult:
    """Recalcula la decisión con sus entradas guardadas y las reglas vigentes; un rechazo o una cotización sin entradas no se pueden (409)."""
    row = await _load(session, decision_id)
    if not isinstance(row, QuoteDB):
        raise RejectionNotReplayableError()
    if not _quote_replayable(row):
        raise DecisionNotReplayableError()
    differences = _replay_quote(row)
    log_audit_event("DECISION_REPLAYED", operator, decision_id=decision_id, matches=not differences)
    return ReplayResult(
        decision_id=decision_id,
        matches=not differences,
        differences=differences,
        rules_version_then=row.rules_version,
        rules_version_now=rules.RULES_VERSION,
    )


async def revoke_consent(session: AsyncSession, document_number: str, operator: str) -> ConsentRevocation:
    """Registra la revocación del titular: los consentimientos GRANTED pasan a REVOKED y dejan de servir para cotizar."""
    revoked = await profile_service.revoke_consents_async(session, document_number)
    log_audit_event("CONSENT_REVOKED_BY_OPERATOR", operator, consents_revoked=revoked)
    return ConsentRevocation(document_masked=mask_document(document_number), revoked=revoked)
