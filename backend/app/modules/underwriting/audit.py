"""Auditoría de suscripción: detalle de una decisión y recálculo con las mismas funciones puras (mejora FE-WEB-08)."""
import json
import logging
from datetime import date, datetime, timezone
from typing import Any, List, Literal, Optional, Union

from pydantic import BaseModel, model_serializer
from sqlalchemy.ext.asyncio import AsyncSession

from app.modules.profile.service import profile_service
from app.modules.rating import service as rating
from app.modules.rating.models import Coverage, PlanTier, PremiumFactor, QuoteDB
from app.modules.rating.partners import operator_ref
from app.modules.underwriting import rules
from app.modules.underwriting.models import UnderwritingRejectionDB

audit_logger = logging.getLogger("solventa.audit")

QUOTE_PREFIX = "QUO-"
REJECTION_NOT_REPLAYABLE = (
    "Los rechazos no guardan el puntaje del buró (HU-WEB-07), así que no se pueden recalcular."
)
CONSENT_REVOKED_NOT_REPLAYABLE = (
    "El consentimiento del titular fue revocado: no se recalcula con sus datos personales."
)
# Diferencias menores a medio centavo o a medio milésimo de multiplicador no cuentan.
FLOAT_TOLERANCE = 0.005


class DecisionNotFoundError(Exception):
    pass


class DecisionNotReplayableError(Exception):
    pass


class RejectionNotReplayableError(Exception):
    pass


class ConsentRevokedNotReplayableError(Exception):
    pass


class DecisionInputs(BaseModel):
    document_masked: Optional[str]
    birth_date: Optional[date]
    insured_amount: Optional[float]
    occupation_risk: Optional[int]
    score: Optional[float]


class RejectionInputs(BaseModel):
    """Lo que guarda un rechazo: sin puntaje del buró ni fecha de nacimiento (CTL-03 de HU-WEB-07).
    Con el consentimiento revocado no lleva el monto solicitado."""
    document_masked: Optional[str]
    insured_amount: Optional[float] = None

    @model_serializer(mode="wrap")
    def _without_omitted_amount(self, handler):
        data = handler(self)
        if self.insured_amount is None:
            data.pop("insured_amount", None)
        return data


class TierOutcome(BaseModel):
    """Con el consentimiento revocado no lleva `breakdown`: sus etiquetas dicen la edad y el multiplicador
    de historial de pago delata la franja del puntaje."""
    tier: str
    monthly_premium: float
    annual_premium: Optional[float] = None
    coverages: List[Coverage] = []
    breakdown: Optional[List[PremiumFactor]] = None

    @model_serializer(mode="wrap")
    def _without_empty_breakdown(self, handler):
        data = handler(self)
        if self.breakdown is None:
            data.pop("breakdown", None)
        return data


REJECTION_FIELDS = ("rule_id", "reason", "max_approvable_amount")


class DecisionOutcome(BaseModel):
    """Los campos del rechazo solo van cuando tienen valor: con el consentimiento revocado se omiten porque
    el cupo máximo sale de la franja del buró o de la edad."""
    status: str
    degradation_reason: Optional[str] = None
    tiers: List[TierOutcome] = []
    rule_id: Optional[str] = None
    reason: Optional[str] = None
    max_approvable_amount: Optional[float] = None

    @model_serializer(mode="wrap")
    def _without_empty_rejection(self, handler):
        data = handler(self)
        for name in REJECTION_FIELDS:
            if getattr(self, name) is None:
                data.pop(name, None)
        return data


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
    """Edad a la fecha de la decisión, no a la de hoy, en la hora local del servidor como `date.today()` al cotizar."""
    if created_at.tzinfo is None:
        created_at = created_at.replace(tzinfo=timezone.utc)
    return rating.age_on(birth_date, created_at.astimezone().date())


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
                TierOutcome(
                    tier=tier["tier"],
                    monthly_premium=tier["monthly_premium"],
                    annual_premium=tier.get("annual_premium"),
                    coverages=tier.get("coverages", []),
                    breakdown=None if revoked else tier.get("breakdown", []),
                )
                for tier in row.tiers
            ],
        )
        replayable = _quote_replayable(row)
    else:
        if revoked:
            inputs = RejectionInputs(document_masked=None)
            outcome = DecisionOutcome(status="review_required")
        else:
            inputs = RejectionInputs(
                document_masked=mask_document(row.document_number), insured_amount=row.requested_amount
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


def _differs(then: Any, now: Any) -> bool:
    numbers = (int, float)
    if isinstance(then, numbers) and isinstance(now, numbers) and not isinstance(then, bool) and not isinstance(now, bool):
        return abs(float(then) - float(now)) > FLOAT_TOLERANCE
    return then != now


def _compare(prefix: str, then: Optional[dict], now: Optional[dict], attributes: tuple, differences: List[Difference]) -> None:
    for attribute in attributes:
        old = None if then is None else then.get(attribute)
        new = None if now is None else now.get(attribute)
        if _differs(old, new):
            differences.append(Difference(field=f"{prefix}.{attribute}", then=old, now=new))


def _replay_quote(quote: QuoteDB) -> List[Difference]:
    age = _age_then(_day(quote.birth_date), quote.created_at)
    score = quote.score_applied
    differences: List[Difference] = []
    decision = rules.evaluate(quote.insured_amount, age, score)
    if decision is not None:
        differences.append(Difference(field="decision", then="approved", now=f"review_required:{decision.rule_id}"))
        return differences
    stored_tiers = {stored["tier"]: stored for stored in quote.tiers}
    offered = {tier.value: tier for tier in rating.OFFERED_TIERS}
    for name in [*stored_tiers, *(name for name in offered if name not in stored_tiers)]:
        stored, tier = stored_tiers.get(name), offered.get(name)
        if stored is None or tier is None:
            differences.append(
                Difference(field=f"{name}.tier", then=name if stored else None, now=name if tier else None)
            )
            continue
        breakdown = rating.build_breakdown(
            quote.insured_amount, age, quote.occupation_risk, score, tier, partial_profile=quote.status == "degraded"
        )
        monthly = float(sum(factor.amount for factor in breakdown))
        premiums = {"monthly_premium": monthly, "annual_premium": monthly * 12}
        _compare(name, stored, premiums, ("monthly_premium", "annual_premium"), differences)
        then_factors = {factor["code"]: factor for factor in stored.get("breakdown", [])}
        now_factors = {factor.code: factor.model_dump() for factor in breakdown}
        for code in sorted(set(then_factors) | set(now_factors)):
            _compare(f"{name}.{code}", then_factors.get(code), now_factors.get(code), ("multiplier", "amount"), differences)
        then_coverages = {coverage["id"]: coverage for coverage in stored.get("coverages", [])}
        now_coverages = {
            coverage.id: coverage.model_dump() for coverage in rating.coverages_for(tier, quote.insured_amount)
        }
        for coverage_id in sorted(set(then_coverages) | set(now_coverages)):
            before, after = then_coverages.get(coverage_id), now_coverages.get(coverage_id)
            prefix = f"{name}.coverages.{coverage_id}"
            if before is None or after is None:
                differences.append(
                    Difference(field=prefix, then=coverage_id if before else None, now=coverage_id if after else None)
                )
                continue
            _compare(prefix, before, after, ("name", "limit", "deductible", "optional", "included"), differences)
    return differences


async def replay(session: AsyncSession, decision_id: str, operator: str) -> ReplayResult:
    """Recalcula la decisión con sus entradas guardadas y las reglas vigentes; un rechazo, una cotización sin
    entradas o con el consentimiento revocado no se recalculan (409)."""
    row = await _load(session, decision_id)
    if not isinstance(row, QuoteDB):
        raise RejectionNotReplayableError()
    if await profile_service.consent_revoked_async(session, row.document_number, row.consent_id):
        raise ConsentRevokedNotReplayableError()
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
