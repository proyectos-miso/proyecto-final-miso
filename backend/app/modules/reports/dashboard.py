"""Tablero operativo del portal de Solventa (FE-WEB-07): indicadores agregados, latencia de las rutas críticas y
estado de las dependencias. Solo cifras y estados: ningún dato personal ni detalle de error sale en la respuesta."""
import asyncio
import logging
import time
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timedelta, timezone
from typing import Awaitable, Callable, Dict, List, Literal, Optional, Tuple

from botocore.exceptions import ClientError
from pydantic import BaseModel
from sqlalchemy import func, select, text
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import settings
from app.core.metrics import GROUPS, TARGETS_MS, LatencyWindow, latency_window, percentile
from app.core.outbox import OutboxMessageDB, SqsQueue, get_message_queue
from app.core.storage import get_document_storage
from app.modules.claims.models import ClaimDB
from app.modules.notifications.service import get_sender
from app.modules.policy.models import PolicyDB
from app.modules.rating.models import QuoteDB
from app.modules.reports.service import COLOMBIA

logger = logging.getLogger("solventa.operations")

Period = Literal["today", "7d", "30d"]
DependencyName = Literal["database", "s3", "sqs", "firebase"]
DependencyState = Literal["ok", "degraded", "down", "not_configured"]

PERIOD_DAYS = {"today": 1, "7d": 7, "30d": 30}
CLOSED_CLAIM_STATUSES = ("SETTLED", "REJECTED", "PAID")
CHECK_TIMEOUT_SECONDS = 1.0
DEGRADED_AFTER_MS = 500
NOT_CONFIGURED = "not_configured"

# Hilos propios: una dependencia colgada no ocupa los del resto del backend.
_check_pool = ThreadPoolExecutor(max_workers=4, thread_name_prefix="dependency-check")


class LatencyGroup(BaseModel):
    group: str
    samples: int
    p50_ms: Optional[float]
    p95_ms: Optional[float]
    p99_ms: Optional[float]
    target_ms: int
    within_target: Optional[bool]


class DependencyStatus(BaseModel):
    name: DependencyName
    status: DependencyState
    latency_ms: Optional[int]


class OperationsIndicators(BaseModel):
    """Los conteos quedan en null si la base no respondió."""
    period: Period
    since: datetime
    generated_at: datetime
    quotes: Optional[int]
    quotes_per_minute: Optional[float]
    conversion: Optional[float]
    policies_issued: Optional[int]
    open_claims: Optional[int]
    outbox_pending: Optional[int]
    latency: List[LatencyGroup]
    dependencies: List[DependencyStatus]


def period_start(period: Period, now: datetime) -> datetime:
    """Desde las 00:00 de Colombia de hoy (today) o de hace 6 y 29 días (7d y 30d)."""
    midnight = now.astimezone(COLOMBIA).replace(hour=0, minute=0, second=0, microsecond=0)
    return midnight - timedelta(days=PERIOD_DAYS[period] - 1)


def safe_ratio(numerator: int, denominator: int, digits: int = 4) -> Optional[float]:
    return round(numerator / denominator, digits) if denominator else None


def latency_groups(window: LatencyWindow) -> List[LatencyGroup]:
    snapshot = window.snapshot()
    groups = []
    for group in GROUPS:
        samples = snapshot.get(group, [])
        p95 = percentile(samples, 95)
        groups.append(LatencyGroup(
            group=group,
            samples=len(samples),
            p50_ms=_ms(percentile(samples, 50)),
            p95_ms=_ms(p95),
            p99_ms=_ms(percentile(samples, 99)),
            target_ms=TARGETS_MS[group],
            within_target=None if p95 is None else p95 <= TARGETS_MS[group],
        ))
    return groups


def _ms(value: Optional[float]) -> Optional[float]:
    return None if value is None else round(value, 1)


async def _counts(session: AsyncSession, start: datetime, now: datetime) -> Dict[str, int]:
    async def count(stmt) -> int:
        return int(await session.scalar(stmt) or 0)

    quotes = await count(select(func.count()).select_from(QuoteDB).where(
        QuoteDB.created_at >= start, QuoteDB.created_at < now))
    converted = await count(select(func.count()).select_from(PolicyDB).join(
        QuoteDB, PolicyDB.quote_id == QuoteDB.quote_id).where(QuoteDB.created_at >= start, QuoteDB.created_at < now))
    issued = await count(select(func.count()).select_from(PolicyDB).where(
        PolicyDB.issued_at >= start, PolicyDB.issued_at < now))
    open_claims = await count(select(func.count()).select_from(ClaimDB).where(
        ClaimDB.status.not_in(CLOSED_CLAIM_STATUSES)))
    outbox = await count(select(func.count()).select_from(OutboxMessageDB).where(OutboxMessageDB.status == "PENDING"))
    return {"quotes": quotes, "converted": converted, "issued": issued, "open_claims": open_claims, "outbox": outbox}


# -- Dependencias ----------------------------------------------------------------------------------

Check = Callable[[], Awaitable[Optional[str]]]


def _in_thread(function: Callable[[], Optional[str]]) -> Check:
    async def run() -> Optional[str]:
        return await asyncio.get_running_loop().run_in_executor(_check_pool, function)
    return run


def _s3() -> Optional[str]:
    storage = get_document_storage()
    try:
        storage.client.head_bucket(Bucket=storage.bucket)
    except ClientError as exc:
        # En AWS el rol no tiene s3:ListBucket (mínimo privilegio): un 403 prueba que S3 responde y el bucket existe.
        if exc.response.get("Error", {}).get("Code") not in ("403", "AccessDenied", "Forbidden"):
            raise
    return None


def _sqs() -> Optional[str]:
    queue = get_message_queue()
    if not isinstance(queue, SqsQueue):
        return NOT_CONFIGURED
    queue.client.get_queue_url(QueueName=queue.queue.rstrip("/").rsplit("/", 1)[-1])
    return None


def _firebase() -> Optional[str]:
    if get_sender() is not None:
        return None
    if not settings.FIREBASE_CREDENTIALS_FILE and not settings.FIREBASE_CREDENTIALS_JSON:
        return NOT_CONFIGURED
    raise RuntimeError("Firebase configurado pero sin iniciar")


def get_dependency_checks() -> Dict[str, Check]:
    """Comprobaciones de S3, SQS y Firebase; las pruebas las reemplazan con `dependency_overrides`."""
    return {"s3": _in_thread(_s3), "sqs": _in_thread(_sqs), "firebase": _in_thread(_firebase)}


async def run_check(name: str, check: Check) -> DependencyStatus:
    """Un segundo como máximo; el motivo de una falla va al registro sin su mensaje, nunca a la respuesta."""
    start = time.perf_counter()
    try:
        outcome = await asyncio.wait_for(check(), CHECK_TIMEOUT_SECONDS)
    except Exception as exc:
        logger.warning("Dependencia %s sin respuesta (%s)", name, type(exc).__name__)
        return DependencyStatus(name=name, status="down", latency_ms=_elapsed(start))
    if outcome == NOT_CONFIGURED:
        return DependencyStatus(name=name, status="not_configured", latency_ms=None)
    elapsed = _elapsed(start)
    return DependencyStatus(name=name, status="degraded" if elapsed >= DEGRADED_AFTER_MS else "ok", latency_ms=elapsed)


def _elapsed(start: float) -> int:
    return round((time.perf_counter() - start) * 1000)


async def check_dependencies(session: AsyncSession, checks: Dict[str, Check]) -> List[DependencyStatus]:
    async def database() -> None:
        await session.execute(text("SELECT 1"))

    names: List[Tuple[str, Check]] = [("database", database), *checks.items()]
    return list(await asyncio.gather(*(run_check(name, check) for name, check in names)))


async def build_indicators(
    session: AsyncSession,
    period: Period,
    checks: Dict[str, Check],
    window: LatencyWindow = latency_window,
    now: Optional[datetime] = None,
) -> OperationsIndicators:
    now = now or datetime.now(timezone.utc)
    start = period_start(period, now)
    dependencies = await check_dependencies(session, checks)
    counts: Optional[Dict[str, int]] = None
    if dependencies[0].status != "down":
        try:
            counts = await _counts(session, start, now)
        except Exception as exc:
            logger.warning("No se pudieron calcular los indicadores (%s)", type(exc).__name__)
            await session.rollback()
    minutes = max(1.0, (now - start).total_seconds() / 60)

    def value(key: str) -> Optional[int]:
        return None if counts is None else counts[key]

    return OperationsIndicators(
        period=period,
        since=start,
        generated_at=now,
        quotes=value("quotes"),
        quotes_per_minute=None if counts is None else round(counts["quotes"] / minutes, 4),
        conversion=None if counts is None else safe_ratio(counts["converted"], counts["quotes"]),
        policies_issued=value("issued"),
        open_claims=value("open_claims"),
        outbox_pending=value("outbox"),
        latency=latency_groups(window),
        dependencies=dependencies,
    )
