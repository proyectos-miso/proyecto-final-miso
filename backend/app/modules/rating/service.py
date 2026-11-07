"""Motor actuarial: desglose de la prima, degradación por latencia del buró, reglas de suscripción,
canal de socios B2B e idempotencia (HU-WEB-03 a 05, HU-WEB-07, HU-WEB-10, HU-WEB-11)."""
import asyncio
import math
import uuid
from datetime import date, datetime, timedelta, timezone
from typing import Awaitable, Callable, List, Optional, Tuple

from sqlalchemy import select, update
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import settings
from app.core.security import get_password_hash
from app.modules.underwriting import rules as underwriting_rules
from app.modules.underwriting.service import new_rejection_id, record_rejection
from app.modules.rating.models import (
    Coverage,
    PartnerDB,
    PartnerQuoteResponse,
    PartnerQuotaExceededResponse,
    PartnerQuotaStatus,
    PartnerStatus,
    PlanTier,
    PremiumFactor,
    QuoteDB,
    QuoteRequest,
    QuoteResponse,
    QuoteTierDetail,
)


class PartnerQuotaExceededError(Exception):
    """Lanzada cuando un socio de distribución B2B supera su cuota de cotizaciones contratada (HU-WEB-12)."""

    def __init__(
        self,
        partner_id: str,
        quota_limit: int,
        quota_used: int,
        reset_at: datetime,
        retry_after: int,
    ):
        self.partner_id = partner_id
        self.quota_limit = quota_limit
        self.quota_used = quota_used
        self.reset_at = reset_at
        self.retry_after = max(1, retry_after)
        super().__init__(
            f"El socio '{partner_id}' superó su cuota contratada ({quota_used}/{quota_limit}). "
            f"Reintentar en {self.retry_after} segundos (renueva a las {reset_at.isoformat()})."
        )

DEFAULT_PARTNERS = [
    {
        "partner_id": "banco-aliado-1",
        "client_id": "banco_aliado",
        "client_secret": "AliadoSecret2026*",  # sdd:allow-secret
        "name": "Banco Aliado S.A.",
        "status": PartnerStatus.ACTIVE.value,
        "quota_limit": 50000,
        "quota_used": 0,
        "quota_window_seconds": 3600,
    },
    {
        "partner_id": "socio-inactivo-1",
        "client_id": "socio_inactivo",
        "client_secret": "Inactivo2026*",  # sdd:allow-secret
        "name": "Financiera Inactiva S.A.",
        "status": PartnerStatus.REVOKED.value,
        "quota_limit": 1000,
        "quota_used": 0,
        "quota_window_seconds": 3600,
    },
]

# Tasa mensual por peso asegurado del prototipo comercial (perfil de referencia: 31-40 años).
BASE_MONTHLY_RATE = 0.0002736
QUOTE_VALIDITY = timedelta(days=15)
NEUTRAL_SCORE = 700
# Recargo de la cotización con perfil parcial, el mismo de los prototipos web.
PARTIAL_PROFILE_SURCHARGE = 1.04

AGE_BANDS: List[Tuple[int, float]] = [(30, 0.92), (40, 1.0), (50, 1.18), (200, 1.42)]
OCCUPATION_MULTIPLIERS = {1: 1.0, 2: 1.1, 3: 1.25, 4: 1.45, 5: 1.7}
SCORE_BANDS: List[Tuple[int, float]] = [(800, 0.9), (650, 1.0), (500, 1.15), (0, 1.3)]
TIER_MULTIPLIERS = {PlanTier.BASIC: 0.75, PlanTier.STANDARD: 1.0, PlanTier.PREMIUM: 1.35}

ScoreProvider = Callable[[QuoteRequest, float], Awaitable[int]]


async def neutral_score_provider(request: QuoteRequest, simulated_delay: float) -> int:
    """Sustituto del buró: devuelve el puntaje neutro tras el retardo simulado."""
    if simulated_delay > 0:
        await asyncio.sleep(simulated_delay)
    return NEUTRAL_SCORE


def age_on(birth_date: date, today: date) -> int:
    return today.year - birth_date.year - ((today.month, today.day) < (birth_date.month, birth_date.day))


def age_multiplier(age: int) -> float:
    return next(multiplier for limit, multiplier in AGE_BANDS if age <= limit)


def score_multiplier(score: int) -> float:
    return next(multiplier for floor, multiplier in SCORE_BANDS if score >= floor)


def build_breakdown(
    insured_amount: float,
    age: int,
    occupation_risk: int,
    score: int,
    tier: PlanTier,
    partial_profile: bool = False,
) -> List[PremiumFactor]:
    """Cada factor aporta lo que su multiplicador agrega sobre el subtotal acumulado; la suma es la prima."""
    base = round(insured_amount * BASE_MONTHLY_RATE)
    steps = [
        ("age", f"Ajuste por edad ({age} años)", age_multiplier(age)),
        ("occupation", f"Ajuste por riesgo de ocupación (nivel {occupation_risk})", OCCUPATION_MULTIPLIERS[occupation_risk]),
        ("payment_history", "Ajuste por historial de pago", score_multiplier(score)),
        ("plan", f"Ajuste por plan {tier.value}", TIER_MULTIPLIERS[tier]),
    ]
    if partial_profile:
        steps.append(("partial_profile", "Recargo por perfil parcial", PARTIAL_PROFILE_SURCHARGE))
    breakdown = [PremiumFactor(code="base_rate", label="Tarifa base actuarial", multiplier=1.0, amount=base)]
    subtotal = float(base)
    for code, label, multiplier in steps:
        amount = round(subtotal * (multiplier - 1))
        breakdown.append(PremiumFactor(code=code, label=label, multiplier=multiplier, amount=amount))
        subtotal += amount
    return breakdown


def coverages_for(tier: PlanTier, insured_amount: float) -> List[Coverage]:
    coverages = [Coverage(id="death", name="Muerte por cualquier causa", limit=insured_amount, deductible=0)]
    if tier in (PlanTier.STANDARD, PlanTier.PREMIUM):
        coverages.append(
            Coverage(id="disability", name="Incapacidad total y permanente", limit=insured_amount, deductible=0)
        )
    if tier == PlanTier.PREMIUM:
        coverages.append(
            Coverage(id="critical_illness", name="Enfermedades graves", limit=insured_amount * 0.5, deductible=0)
        )
    return coverages


class RatingService:
    """Motor actuarial en memoria con protección de latencia (ASR-01, ASR-02, HA-01, HA-05)."""

    def __init__(
        self,
        bureau_timeout: float = settings.BUREAU_TIMEOUT_SECONDS,
        score_provider: ScoreProvider = neutral_score_provider,
        today: Callable[[], date] = date.today,
    ):
        self.bureau_timeout = bureau_timeout
        self._score_provider = score_provider
        self._today = today

    async def calculate_quote(
        self,
        request: QuoteRequest,
        simulated_bureau_delay: float = 0.0,
    ) -> QuoteResponse:
        try:
            score = await asyncio.wait_for(self._score_provider(request, simulated_bureau_delay), self.bureau_timeout)
            degradation_reason = None
        except TimeoutError:
            score, degradation_reason = NEUTRAL_SCORE, "TIMEOUT_EXCEEDED"
        age = age_on(date.fromisoformat(request.birth_date), self._today())
        created_at = datetime.now(timezone.utc)

        decision = underwriting_rules.evaluate(request.insured_amount, age, score)
        if decision is not None:
            return QuoteResponse(
                quote_id=new_rejection_id(),
                document_number=request.document_number,
                status="review_required",
                degradation_reason=degradation_reason,
                score_applied=score,
                tiers=[],
                created_at=created_at.isoformat(),
                expires_at=created_at.isoformat(),
                underwriting=decision,
            )

        tiers = []
        for tier in PlanTier:
            breakdown = build_breakdown(
                request.insured_amount, age, request.occupation_risk, score, tier, partial_profile=bool(degradation_reason)
            )
            monthly = float(sum(factor.amount for factor in breakdown))
            tiers.append(
                QuoteTierDetail(
                    tier=tier,
                    monthly_premium=monthly,
                    annual_premium=monthly * 12,
                    coverages=coverages_for(tier, request.insured_amount),
                    breakdown=breakdown,
                )
            )

        return QuoteResponse(
            quote_id=f"QUO-{uuid.uuid4().hex[:12].upper()}",
            document_number=request.document_number,
            status="degraded" if degradation_reason else "active",
            degradation_reason=degradation_reason,
            score_applied=score,
            tiers=tiers,
            created_at=created_at.isoformat(),
            expires_at=(created_at + QUOTE_VALIDITY).isoformat(),
        )

    async def create_quote(
        self,
        session: AsyncSession,
        request: QuoteRequest,
        simulated_bureau_delay: float = 0.0,
        partner_id: Optional[str] = None,
        commit: bool = True,
        increment_quota: bool = True,
    ) -> QuoteResponse:
        """Calcula la cotización y la guarda para que la emisión compruebe su vigencia (HU-WEB-05, HU-WEB-10, HA-17).
        Un rechazo de suscripción se registra aparte y no deja cotización emitible (HU-WEB-07)."""
        quote = await self.calculate_quote(request, simulated_bureau_delay)
        if quote.underwriting is not None:
            await record_rejection(
                session,
                rejection_id=quote.quote_id,
                document_number=quote.document_number,
                requested_amount=request.insured_amount,
                decision=quote.underwriting,
                created_at=datetime.fromisoformat(quote.created_at),
                commit=commit,
            )
            return quote
        session.add(
            QuoteDB(
                quote_id=quote.quote_id,
                partner_id=partner_id,
                document_number=quote.document_number,
                status=quote.status,
                degradation_reason=quote.degradation_reason,
                score_applied=quote.score_applied,
                tiers=[tier.model_dump(mode="json") for tier in quote.tiers],
                created_at=datetime.fromisoformat(quote.created_at),
                expires_at=datetime.fromisoformat(quote.expires_at),
            )
        )
        if partner_id and increment_quota:
            await session.execute(
                update(PartnerDB)
                .where(PartnerDB.partner_id == partner_id)
                .values(quota_used=PartnerDB.quota_used + 1)
            )
        if commit:
            await session.commit()
        return quote

    async def get_or_init_partner_window(
        self,
        session: AsyncSession,
        partner_id: str,
        now: Optional[datetime] = None,
    ) -> Tuple[PartnerDB, datetime]:
        """Obtiene el socio y asegura que su ventana de cuota esté inicializada y vigente (HU-WEB-12)."""
        current_time = now or datetime.now(timezone.utc)
        if current_time.tzinfo is None:
            current_time = current_time.replace(tzinfo=timezone.utc)

        stmt = select(PartnerDB).where(PartnerDB.partner_id == partner_id)
        result = await session.execute(stmt)
        partner = result.scalars().first()
        if not partner:
            raise ValueError(f"Socio '{partner_id}' no encontrado.")

        window_seconds = getattr(partner, "quota_window_seconds", None) or settings.PARTNER_QUOTA_WINDOW_SECONDS
        reset_at = getattr(partner, "quota_reset_at", None)
        if reset_at is not None and reset_at.tzinfo is None:
            reset_at = reset_at.replace(tzinfo=timezone.utc)

        # Si no tiene fecha de reinicio inicial, se fija la ventana sin alterar el contador:
        if reset_at is None:
            partner.quota_reset_at = current_time + timedelta(seconds=window_seconds)
        # Si ya se cumplió el tiempo de la ventana previa, reiniciamos el contador y fijamos nueva fecha:
        elif current_time >= reset_at:
            partner.quota_used = 0
            partner.quota_reset_at = current_time + timedelta(seconds=window_seconds)

        return partner, current_time

    async def consume_partner_quota(
        self,
        session: AsyncSession,
        partner_id: str,
        now: Optional[datetime] = None,
        commit: bool = False,
    ) -> PartnerQuotaStatus:
        """
        Valida y consume una solicitud de la cuota del socio en la ventana activa (HU-WEB-12).
        Lanza PartnerQuotaExceededError si la cuota fue superada.
        """
        partner, current_time = await self.get_or_init_partner_window(session, partner_id, now)
        reset_at = partner.quota_reset_at
        if reset_at.tzinfo is None:
            reset_at = reset_at.replace(tzinfo=timezone.utc)

        retry_after = max(1, math.ceil((reset_at - current_time).total_seconds()))

        if partner.quota_used >= partner.quota_limit:
            raise PartnerQuotaExceededError(
                partner_id=partner.partner_id,
                quota_limit=partner.quota_limit,
                quota_used=partner.quota_used,
                reset_at=reset_at,
                retry_after=retry_after,
            )

        partner.quota_used += 1
        if commit:
            await session.commit()

        remaining = max(0, partner.quota_limit - partner.quota_used)
        return PartnerQuotaStatus(
            partner_id=partner.partner_id,
            quota_limit=partner.quota_limit,
            quota_used=partner.quota_used,
            quota_remaining=remaining,
            quota_window_seconds=partner.quota_window_seconds or settings.PARTNER_QUOTA_WINDOW_SECONDS,
            reset_at=reset_at,
            retry_after=0,
        )

    async def get_partner_quota_status(
        self,
        session: AsyncSession,
        partner_id: str,
        now: Optional[datetime] = None,
    ) -> PartnerQuotaStatus:
        """
        Consulta el estado actual de cuota y ventana de un socio sin incrementar quota_used (HU-WEB-12).
        """
        partner, current_time = await self.get_or_init_partner_window(session, partner_id, now)
        reset_at = partner.quota_reset_at
        if reset_at.tzinfo is None:
            reset_at = reset_at.replace(tzinfo=timezone.utc)

        retry_after = (
            max(1, math.ceil((reset_at - current_time).total_seconds()))
            if partner.quota_used >= partner.quota_limit
            else 0
        )
        remaining = max(0, partner.quota_limit - partner.quota_used)
        return PartnerQuotaStatus(
            partner_id=partner.partner_id,
            quota_limit=partner.quota_limit,
            quota_used=partner.quota_used,
            quota_remaining=remaining,
            quota_window_seconds=partner.quota_window_seconds or settings.PARTNER_QUOTA_WINDOW_SECONDS,
            reset_at=reset_at,
            retry_after=retry_after,
        )

    async def get_partner_quote(
        self,
        session: AsyncSession,
        quote_id: str,
        partner_id: str,
    ) -> Optional[PartnerQuoteResponse]:
        """Obtiene una cotización solo si existe y pertenece al socio autenticado (HU-WEB-10)."""
        stmt = select(QuoteDB).where(QuoteDB.quote_id == quote_id)
        result = await session.execute(stmt)
        quote = result.scalars().first()
        if not quote or not quote.partner_id or quote.partner_id != partner_id:
            return None
        return PartnerQuoteResponse.from_db(quote)

    async def list_partner_quotes(
        self,
        session: AsyncSession,
        partner_id: str,
    ) -> List[PartnerQuoteResponse]:
        """Lista el histórico de cotizaciones del socio autenticado (HU-WEB-10)."""
        stmt = (
            select(QuoteDB)
            .where(QuoteDB.partner_id == partner_id)
            .order_by(QuoteDB.created_at.desc())
        )
        result = await session.execute(stmt)
        quotes = result.scalars().all()
        return [PartnerQuoteResponse.from_db(q) for q in quotes]

    async def seed_default_partners(self, session: AsyncSession) -> List[PartnerDB]:
        """Siembra de forma idempotente los socios B2B semilla (activo y revocado) en la base de datos (HU-WEB-10)."""
        seeded: List[PartnerDB] = []
        for item in DEFAULT_PARTNERS:
            stmt = select(PartnerDB).where(PartnerDB.client_id == item["client_id"])
            existing = (await session.execute(stmt)).scalars().first()
            if existing:
                seeded.append(existing)
                continue
            partner = PartnerDB(
                partner_id=item["partner_id"],
                client_id=item["client_id"],
                client_secret_hash=item.get("client_secret_hash") or get_password_hash(item.get("client_secret", "")),
                name=item["name"],
                status=item["status"],
                quota_limit=item["quota_limit"],
                quota_used=item["quota_used"],
                quota_window_seconds=item.get("quota_window_seconds", settings.PARTNER_QUOTA_WINDOW_SECONDS),
                quota_reset_at=item.get("quota_reset_at"),
            )
            session.add(partner)
            seeded.append(partner)
        await session.commit()
        return seeded


rating_service = RatingService()
