"""Punto de entrada principal del backend monolito modular de Solventa."""
from contextlib import asynccontextmanager
from datetime import datetime, timezone
from fastapi import FastAPI, status
from fastapi.middleware.cors import CORSMiddleware
from sqlalchemy import text
from app.core.config import settings
from app.core.database import engine, Base, AsyncSessionLocal
from app.core.middleware import TenantContextMiddleware
from app.core.storage import ensure_emulator_bucket
from app.bff.experience.router import router as experience_router
from app.bff.experience.operations_router import router as operations_router
from app.bff.quote_edge.router import router as quote_edge_router
from app.bff.auth.router import router as auth_router
from app.bff.insured.router import router as insured_router
from app.modules.auth.models import UserDB  # noqa: F401 - registrar tablas en Base.metadata
from app.modules.rating.models import PartnerDB, QuoteDB  # noqa: F401 - registrar tablas en Base.metadata
from app.modules.policy.models import PolicyDB  # noqa: F401 - registrar tablas en Base.metadata
from app.modules.claims.models import ClaimDB  # noqa: F401 - registrar tablas en Base.metadata
from app.modules.claims.service import claim_service
from app.core.outbox import OutboxMessageDB  # noqa: F401 - registrar tablas en Base.metadata
from app.core.idempotency import IdempotencyRecordDB  # noqa: F401 - registrar tablas en Base.metadata
from app.modules.payments.models import PaymentTransactionDB  # noqa: F401 - registrar tablas en Base.metadata
from app.modules.profile.models import FinancialEntityDB, CityDB  # noqa: F401 - registrar tablas en Base.metadata
from app.modules.identity import db_models as identity_db_models  # noqa: F401 - registrar tablas en Base.metadata
from app.modules.auth.service import auth_service
from app.modules.profile.service import profile_service
from app.modules.rating.service import rating_service
from app.modules.identity.service import insured_auth_service
from app.modules.policy.search import create_search_indexes


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Inicialización de tablas relacionales, usuario asesor y socios B2B semilla (HU-WEB-13, HU-WEB-10)."""
    insured_auth_service.sms  # falla al arrancar si la configuración del SMS está incompleta
    try:
        async with engine.begin() as conn:
            await conn.run_sync(Base.metadata.create_all)
            await conn.execute(text("ALTER TABLE quotes ADD COLUMN IF NOT EXISTS partner_id VARCHAR(64)"))
            await conn.execute(text("CREATE INDEX IF NOT EXISTS ix_quotes_partner_id ON quotes (partner_id)"))
            # HU-WEB-08: socio de la póliza (faltaba desde HU-WEB-14), usuarios ligados a un socio y titular
            # de la póliza en bases ya creadas.
            await conn.execute(text(
                "ALTER TABLE policies ADD COLUMN IF NOT EXISTS partner_id VARCHAR(64) REFERENCES partners(partner_id)"
            ))
            await conn.execute(text("CREATE INDEX IF NOT EXISTS ix_policies_partner_id ON policies (partner_id)"))
            await conn.execute(text(
                "ALTER TABLE users ADD COLUMN IF NOT EXISTS partner_id VARCHAR(64) REFERENCES partners(partner_id)"
            ))
            await conn.execute(text("ALTER TABLE users ADD COLUMN IF NOT EXISTS last_totp_step INTEGER"))
            await conn.execute(text(
                "ALTER TABLE policies ADD COLUMN IF NOT EXISTS holder_name VARCHAR(150), "
                "ADD COLUMN IF NOT EXISTS holder_document_type VARCHAR(8), "
                "ADD COLUMN IF NOT EXISTS holder_email VARCHAR(254), "
                "ADD COLUMN IF NOT EXISTS holder_phone VARCHAR(20)"
            ))
            # HU-WEB-16: clave de la carátula en S3, vacía hasta el primer pedido.
            await conn.execute(text("ALTER TABLE policies ADD COLUMN IF NOT EXISTS pdf_s3_key VARCHAR(64)"))
            await conn.execute(text("CREATE INDEX IF NOT EXISTS ix_policies_insured_document ON policies (insured_document)"))
            # HU-WEB-15: versión de la póliza por endosos y datos del siniestro para el expediente y el dictamen.
            await conn.execute(text("ALTER TABLE policies ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 1"))
            await conn.execute(text(
                "ALTER TABLE claims ADD COLUMN IF NOT EXISTS origin VARCHAR(16) NOT NULL DEFAULT 'APP', "
                "ADD COLUMN IF NOT EXISTS coverage_id VARCHAR(32), "
                "ADD COLUMN IF NOT EXISTS description TEXT, "
                "ADD COLUMN IF NOT EXISTS parametric_event JSON, "
                "ADD COLUMN IF NOT EXISTS assigned_to VARCHAR(120), "
                "ADD COLUMN IF NOT EXISTS expert_id VARCHAR(32) REFERENCES experts(expert_id), "
                "ADD COLUMN IF NOT EXISTS settled_amount DOUBLE PRECISION"
            ))
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
    yield


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
)

# 2. Middleware de Contexto Multi-Tenant (ASR-06 / HA-14)
app.add_middleware(TenantContextMiddleware)

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


@app.get("/", tags=["Root"])
async def root():
    return {
        "message": f"Bienvenido a {settings.PROJECT_NAME}",
        "docs": "/docs" if settings.debug_enabled else None,
        "health": "/health",
    }
