"""Tablero operativo del portal de Solventa (FE-WEB-07): indicadores agregados, latencia de las rutas críticas y
estado de las dependencias. Solo cifras y estados: ningún dato personal ni detalle de error sale en la respuesta."""
import asyncio
import logging
import threading
import time
from datetime import datetime, timedelta, timezone
from functools import lru_cache
from typing import Awaitable, Callable, Dict, List, Literal, Optional, Tuple

import boto3
from botocore.config import Config
from botocore.exceptions import ClientError
from pydantic import BaseModel
from sqlalchemy import func, select, text
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import settings
from app.core.metrics import GROUPS, TARGETS_MS, LatencyWindow, latency_window, percentile
from app.core.outbox import OutboxMessageDB
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
COUNTS_TIMEOUT_MS = 2000
CACHE_SECONDS = 15.0
MAX_CHECK_THREADS = 4
DATABASE_PROBE = "SELECT 1"
NOT_CONFIGURED = "not_configured"
COUNT_KEYS = ("quotes", "converted", "issued", "open_claims", "outbox")

# Clientes de las sondas, distintos de los de storage.py y outbox.py: un intento y 1 s por conexión y por lectura,
# así un hilo colgado se libera en unos 2 s.
PROBE_CONFIG = Config(connect_timeout=1, read_timeout=1, retries={"total_max_attempts": 1})


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


def counts_statement(start: datetime, now: datetime):
    """Los cinco conteos en una sola sentencia."""
    in_period = (QuoteDB.created_at >= start, QuoteDB.created_at < now)
    subqueries = {
        "quotes": select(func.count()).select_from(QuoteDB).where(*in_period),
        "converted": select(func.count()).select_from(PolicyDB).join(
            QuoteDB, PolicyDB.quote_id == QuoteDB.quote_id).where(*in_period),
        "issued": select(func.count()).select_from(PolicyDB).where(
            PolicyDB.issued_at >= start, PolicyDB.issued_at < now),
        "open_claims": select(func.count()).select_from(ClaimDB).where(ClaimDB.status.not_in(CLOSED_CLAIM_STATUSES)),
        "outbox": select(func.count()).select_from(OutboxMessageDB).where(OutboxMessageDB.status == "PENDING"),
    }
    return select(*(query.scalar_subquery().label(key) for key, query in subqueries.items()))


async def _counts(session: AsyncSession, start: datetime, now: datetime) -> Dict[str, int]:
    """Con `statement_timeout` local a la transacción: si el conteo tarda, la base lo corta y el pedido sigue."""
    await session.execute(select(func.set_config("statement_timeout", str(COUNTS_TIMEOUT_MS), True)))
    row = (await session.execute(counts_statement(start, now))).mappings().one()
    return {key: int(row[key] or 0) for key in COUNT_KEYS}


# -- Dependencias ----------------------------------------------------------------------------------

Check = Callable[[], Awaitable[Optional[str]]]

# Hilos daemon, a lo sumo MAX_CHECK_THREADS a la vez: una dependencia colgada no ocupa los hilos del resto del
# backend ni retrasa el apagado del proceso.
_check_slots = threading.BoundedSemaphore(MAX_CHECK_THREADS)


def _in_thread(function: Callable[[], Optional[str]]) -> Check:
    async def run() -> Optional[str]:
        if not _check_slots.acquire(blocking=False):
            raise RuntimeError("Sin hilos libres para la comprobación")
        loop = asyncio.get_running_loop()
        future: asyncio.Future = loop.create_future()

        def settle(result: Optional[str], error: Optional[BaseException]) -> None:
            if future.done():
                return
            if error is None:
                future.set_result(result)
            else:
                future.set_exception(error)

        def work() -> None:
            result, error = None, None
            try:
                result = function()
            except BaseException as exc:
                error = exc
            finally:
                _check_slots.release()
            try:
                loop.call_soon_threadsafe(settle, result, error)
            except RuntimeError:  # el ciclo de eventos ya cerró
                pass

        threading.Thread(target=work, name="dependency-check", daemon=True).start()
        return await future
    return run


def _probe_client(service: str, **extra):
    endpoint = settings.AWS_ENDPOINT_URL
    # Como storage.py y outbox.py: con el emulador, credenciales de prueba y una sesión propia.
    emulator = {"aws_access_key_id": "test", "aws_secret_access_key": "test"} if endpoint else {}
    return boto3.session.Session().client(
        service, region_name=settings.AWS_REGION, endpoint_url=endpoint,
        config=PROBE_CONFIG.merge(Config(**extra)), **emulator,
    )


@lru_cache
def s3_probe_client():
    return _probe_client("s3", s3={"addressing_style": "path" if settings.AWS_ENDPOINT_URL else "auto"})


@lru_cache
def sqs_probe_client():
    return _probe_client("sqs")


def _s3() -> Optional[str]:
    if not settings.S3_BUCKET_POLICIES:
        return NOT_CONFIGURED
    try:
        s3_probe_client().head_bucket(Bucket=settings.S3_BUCKET_POLICIES)
    except ClientError as exc:
        # En AWS el rol no tiene s3:ListBucket (mínimo privilegio) y el 403 cuenta como «responde»: ver RISK-02.
        if exc.response.get("Error", {}).get("Code") not in ("403", "AccessDenied", "Forbidden"):
            raise
    return None


def _sqs() -> Optional[str]:
    if not settings.SQS_QUEUE_EVENTS:
        return NOT_CONFIGURED
    sqs_probe_client().get_queue_url(QueueName=settings.SQS_QUEUE_EVENTS.rstrip("/").rsplit("/", 1)[-1])
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


def database_check(session: AsyncSession) -> Check:
    """Con su propia conexión del pool: si el límite la corta, la sesión del pedido queda intacta."""
    async def database() -> None:
        async with session.bind.connect() as connection:
            await connection.execute(text(DATABASE_PROBE))
    return database


async def check_dependencies(session: AsyncSession, checks: Dict[str, Check]) -> List[DependencyStatus]:
    names: List[Tuple[str, Check]] = [("database", database_check(session)), *checks.items()]
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
            try:
                await session.rollback()
            except Exception as rollback_error:
                logger.warning("No se pudo deshacer la transacción (%s)", type(rollback_error).__name__)
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


# Respuesta completa por periodo durante CACHE_SECONDS en la memoria de la réplica: varias pestañas abiertas no
# multiplican las comprobaciones ni los conteos.
_cache: Dict[str, Tuple[float, OperationsIndicators]] = {}


async def cached_indicators(session: AsyncSession, period: Period, checks: Dict[str, Check]) -> OperationsIndicators:
    hit = _cache.get(period)
    if hit is not None and hit[0] > time.monotonic():
        return hit[1]
    indicators = await build_indicators(session, period, checks)
    _cache[period] = (time.monotonic() + CACHE_SECONDS, indicators)
    return indicators


def clear_cache() -> None:
    _cache.clear()
