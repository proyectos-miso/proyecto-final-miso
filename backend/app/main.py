"""Punto de entrada principal del backend monolito modular de Solventa."""
import asyncio
from contextlib import asynccontextmanager
from datetime import datetime, timezone
from fastapi import FastAPI, status
from fastapi.middleware.cors import CORSMiddleware
from app.core.config import settings
from app.core.database import engine, Base, AsyncSessionLocal
from app.core.metrics import LatencyMiddleware
from app.core.middleware import TenantContextMiddleware
from app.core.storage import ensure_emulator_bucket
from app.bff.experience.router import router as experience_router
from app.bff.experience.operations_router import router as operations_router
from app.bff.quote_edge.router import router as quote_edge_router
from app.bff.auth.router import router as auth_router
from app.bff.insured.router import router as insured_router
from app.bff.oracle.router import router as oracle_router
from app.modules.auth.models import UserDB  # noqa: F401 - registrar tablas en Base.metadata
from app.modules.rating.models import PartnerDB, QuoteDB  # noqa: F401 - registrar tablas en Base.metadata
from app.modules.policy.models import PolicyDB, PolicySignatureDB  # noqa: F401 - registrar tablas en Base.metadata
from app.modules.claims.models import ClaimDB  # noqa: F401 - registrar tablas en Base.metadata
from app.modules.claims.service import claim_service
from app.modules.claims.oracle import OracleEventDB  # noqa: F401 - registrar tablas
from app.modules.notifications.service import PushPreferencesDB, PushTokenDB, notification_service  # noqa: F401 - registrar tablas
from app.modules.policy.operations import policy_operations
from app.core.migrations import upgrade_database
from app.core.outbox import (  # noqa: F401 - registrar tablas en Base.metadata
    PUBLISH_EVERY_SECONDS, OutboxMessageDB, ProcessedMessageDB, events_queue_configured, outbox_consumer, outbox_publisher,
)
from app.core.idempotency import IdempotencyRecordDB  # noqa: F401 - registrar tablas en Base.metadata
from app.modules.payments.models import PaymentTransactionDB  # noqa: F401 - registrar tablas en Base.metadata
from app.modules.profile.models import FinancialEntityDB, CityDB, ConsentDB  # noqa: F401 - registrar tablas en Base.metadata
from app.modules.identity import db_models as identity_db_models  # noqa: F401 - registrar tablas en Base.metadata
from app.modules.auth.service import auth_service
from app.modules.profile.service import profile_service
from app.modules.rating.service import rating_service
from app.modules.identity.service import insured_auth_service
from app.modules.policy.search import create_search_indexes


async def run_policy_lifecycle() -> None:
    """Vence las pólizas sin renovar y avisa las que vencen en 30 días (HU-MOV-11, RC-24 y RC-27)."""
    async with AsyncSessionLocal() as session:
        await policy_operations.expire_due(session)
        await policy_operations.remind_renewals(session)


async def publish_outbox() -> None:
    """Lleva el outbox a SQS cada pocos segundos; una falla deja los mensajes pendientes para la siguiente vuelta."""
    while True:
        try:
            async with AsyncSessionLocal() as session:
                await outbox_publisher.publish_pending(session)
        except Exception as exc:
            print(f"Aviso: no se pudo publicar el outbox: {exc}")
        await asyncio.sleep(PUBLISH_EVERY_SECONDS)


async def consume_outbox() -> None:
    """Entrega los eventos de la cola a sus manejadores; cada vuelta espera hasta 10 s por mensajes (ADR 0030)."""
    while True:
        try:
            await outbox_consumer.consume(AsyncSessionLocal)
        except Exception as exc:
            print(f"Aviso: no se pudo leer la cola de eventos: {type(exc).__name__}")
            await asyncio.sleep(PUBLISH_EVERY_SECONDS)


async def daily_policy_lifecycle() -> None:
    while True:
        try:
            await run_policy_lifecycle()
        except Exception as exc:
            print(f"Aviso: no se pudo revisar el vencimiento de las pólizas: {exc}")
        await asyncio.sleep(24 * 3600)


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Inicialización de tablas relacionales, usuario asesor y socios B2B semilla (HU-WEB-13, HU-WEB-10)."""
    insured_auth_service.sms  # falla al arrancar si la configuración del SMS está incompleta
    try:
        await upgrade_database()
        try:
            async with engine.begin() as conn:
                await create_search_indexes(conn)
        except Exception as exc:
            print(f"Aviso: sin índices de trigramas para la búsqueda de pólizas: {exc}")
        try:
            await ensure_emulator_bucket()
        except Exception as exc:
            print(f"Aviso: sin bucket de documentos en el emulador: {exc}")
        async with AsyncSessionLocal() as session:
            await profile_service.seed_default_catalogs(session)
            await rating_service.seed_default_partners(session)
            await claim_service.seed_experts(session)
            # Después de los socios: el analista de prueba de Banco Aliado se liga a su socio.
            if settings.debug_enabled:
                await auth_service.seed_default_users(session)
            await insured_auth_service.seed_default_account(session)
    except Exception as exc:
        print(f"Aviso de inicialización de base de datos en arranque: {exc}")
    for event_type, handler in notification_service.event_handlers().items():
        outbox_consumer.register(event_type, handler)
    tasks = [asyncio.create_task(daily_policy_lifecycle()), asyncio.create_task(publish_outbox())]
    if events_queue_configured():
        tasks.append(asyncio.create_task(consume_outbox()))
    yield
    for task in tasks:
        task.cancel()


def docs_urls(enabled: bool) -> dict:
    """Rutas de la documentación interactiva; apagadas cuando ENVIRONMENT es production."""
    if not enabled:
        return {"docs_url": None, "redoc_url": None, "openapi_url": None}
    return {"docs_url": "/docs", "redoc_url": "/redoc", "openapi_url": "/openapi.json"}


app = FastAPI(
    title=settings.PROJECT_NAME,
    version=settings.VERSION,
    description="Backend Monolito Modular API-First para Solventa Seguros Digitales",
    lifespan=lifespan,
    **docs_urls(settings.debug_enabled),
)

# 1. CORS: solo los orígenes de CORS_ORIGINS; el portal y la API comparten origen detrás de CloudFront
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.CORS_ORIGINS,
    allow_credentials=False,
    allow_methods=["GET", "POST", "PUT", "PATCH", "DELETE"],
    allow_headers=["Authorization", "Content-Type", "Idempotency-Key"],
    expose_headers=[
        "Retry-After",
        "X-RateLimit-Limit",
        "X-RateLimit-Remaining",
        "X-RateLimit-Reset",
        "X-RateLimit-Reset-Time",
    ],
)

# 2. Middleware de Contexto Multi-Tenant (ASR-06 / HA-14)
app.add_middleware(TenantContextMiddleware)

# Latencia de las rutas críticas para el tablero operativo (FE-WEB-07); el más externo, mide de punta a punta.
app.add_middleware(LatencyMiddleware)

# 3. Endpoint de Sondeo de Salud para AWS ALB (HA-09: cada 5s, umbral 2)
@app.get("/health", status_code=status.HTTP_200_OK, tags=["Health"])
async def health_check():
    """Endpoint consultado activamente por el ALB de AWS para determinar nodos sanos."""
    return {
        "status": "healthy",
        "service": settings.PROJECT_NAME,
        "version": settings.VERSION,
        "timestamp": datetime.now(timezone.utc).isoformat(),
    }


# 4. Registro de Routers BFF
app.include_router(experience_router, prefix=settings.API_V1_STR)
app.include_router(operations_router, prefix=settings.API_V1_STR)
app.include_router(quote_edge_router, prefix=settings.API_V1_STR)
app.include_router(auth_router, prefix=settings.API_V1_STR)
app.include_router(insured_router, prefix=settings.API_V1_STR)
app.include_router(oracle_router, prefix=settings.API_V1_STR)


@app.get("/", tags=["Root"])
async def root():
    return {
        "message": f"Bienvenido a {settings.PROJECT_NAME}",
        "docs": "/docs" if settings.debug_enabled else None,
        "health": "/health",
    }
