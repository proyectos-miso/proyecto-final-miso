"""Eventos externos del oráculo (HU-MOV-10, RC-26): un evento firmado que cumple la regla paramétrica crea un siniestro
con radicado para cada póliza vigente de la ciudad afectada, y esa regla lo liquida sin analista (HU-WEB-15)."""
import hashlib
import hmac
import unicodedata
from datetime import datetime, timedelta, timezone
from typing import Callable, List, Literal

from pydantic import BaseModel, Field
from sqlalchemy import JSON, Column, DateTime, String, select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import settings
from app.core.database import Base
from app.modules.claims.intake import next_claim_number
from app.modules.claims.parametric import matching_rule
from app.modules.claims.service import claim_service
from app.modules.policy.models import PolicyDB
from app.modules.rating.models import QuoteDB

MAX_CLOCK_SKEW = timedelta(minutes=5)


class OracleEventDB(Base):
    """Evento ya procesado: el mismo `event_id` no vuelve a crear siniestros (AC-06)."""
    __tablename__ = "oracle_events"

    event_id = Column(String(64), primary_key=True)
    event_type = Column(String(32), nullable=False)
    payload = Column(JSON, nullable=False)
    claim_numbers = Column(JSON, nullable=False)
    received_at = Column(DateTime(timezone=True), nullable=False)


class OracleEvent(BaseModel):
    event_id: str = Field(..., pattern=r"^[A-Za-z0-9_.:-]{6,64}$")
    type: Literal["SISMO"]
    magnitude: float = Field(..., ge=0, le=10)
    city: str = Field(..., min_length=2, max_length=80)
    occurred_at: datetime


class OracleResult(BaseModel):
    event_id: str
    claim_numbers: List[str]
    duplicate: bool = False


class OracleDisabledError(Exception):
    pass


class InvalidSignatureError(Exception):
    pass


def signature(secret: str, timestamp: str, body: bytes) -> str:
    return hmac.new(secret.encode(), timestamp.encode() + b"." + body, hashlib.sha256).hexdigest()


def normalized(city: str) -> str:
    plain = unicodedata.normalize("NFKD", city).encode("ascii", "ignore").decode()
    return " ".join(plain.lower().split())


class Oracle:
    def __init__(self, now: Callable[[], datetime] = lambda: datetime.now(timezone.utc)):
        self.now = now

    def verify(self, timestamp: str, provided: str, body: bytes) -> None:
        """HMAC-SHA256 de «marca.cuerpo» con la llave compartida y una marca de tiempo de máximo 5 minutos."""
        if settings.ORACLE_SECRET is None:
            raise OracleDisabledError()
        try:
            moment = datetime.fromtimestamp(int(timestamp), tz=timezone.utc)
        except (TypeError, ValueError, OverflowError):
            raise InvalidSignatureError()
        if abs(self.now() - moment) > MAX_CLOCK_SKEW:
            raise InvalidSignatureError()
        expected = signature(settings.ORACLE_SECRET.get_secret_value(), timestamp, body)
        if not hmac.compare_digest(expected, (provided or "").lower()):
            raise InvalidSignatureError()

    async def receive(self, session: AsyncSession, event: OracleEvent) -> OracleResult:
        known = await session.get(OracleEventDB, event.event_id)
        if known is not None:
            return OracleResult(event_id=event.event_id, claim_numbers=known.claim_numbers, duplicate=True)
        payload = event.model_dump(mode="json")
        rule = matching_rule(payload)
        claims: List[str] = []
        if rule is not None:
            for policy in await self.affected_policies(session, event.city, rule.coverage_id):
                number = await next_claim_number(session, self.now())
                claims.append(number)
                await claim_service.register_claim(
                    session, number, policy.policy_number, self.now(), coverage_id=rule.coverage_id,
                    origin="PARAMETRIC", parametric_event=payload,
                    description=f"Evento {event.type} de magnitud {event.magnitude} en {event.city}",
                    commit=False,
                )
        session.add(OracleEventDB(event_id=event.event_id, event_type=event.type, payload=payload,
                                  claim_numbers=claims, received_at=self.now()))
        try:
            # Los siniestros y el evento en una sola transacción: una entrega simultánea del mismo evento choca con la
            # llave del evento y se deshace entera; después se envían los avisos de los cambios.
            await claim_service.commit(session)
        except IntegrityError:
            await session.rollback()
            session.info.pop("claim_notices", None)
            known = await session.get(OracleEventDB, event.event_id)
            return OracleResult(event_id=event.event_id, claim_numbers=known.claim_numbers if known else claims, duplicate=True)
        return OracleResult(event_id=event.event_id, claim_numbers=claims)

    async def affected_policies(self, session: AsyncSession, city: str, coverage_id: str) -> List[PolicyDB]:
        """Pólizas activas y vigentes cuya cotización es de la ciudad del evento y que incluyen la cobertura de la regla."""
        rows = await session.execute(
            select(PolicyDB, QuoteDB.city).join(QuoteDB, QuoteDB.quote_id == PolicyDB.quote_id)
            .where(PolicyDB.status == "ACTIVE", PolicyDB.valid_until > self.now(), QuoteDB.city.is_not(None))
            .order_by(PolicyDB.policy_number)
        )
        wanted = normalized(city)
        return [
            policy for policy, policy_city in rows
            if normalized(policy_city) == wanted and any(
                c.get("id") == coverage_id and c.get("included", True) for c in (policy.conditions or {}).get("coverages", [])
            )
        ]


oracle = Oracle()
