"""Modelos de datos para el motor de cotización y canal de socios B2B (HU-WEB-10)."""
from datetime import date, datetime
from enum import Enum
from typing import List, Literal, Optional, Union
from pydantic import BaseModel, Field
from sqlalchemy import JSON, Column, DateTime, Float, Integer, String, func
from app.core.database import Base
from app.modules.underwriting.models import UnderwritingDecision


class PlanTier(str, Enum):
    BASIC = "basic"
    STANDARD = "standard"
    PREMIUM = "premium"


class PartnerStatus(str, Enum):
    ACTIVE = "ACTIVE"
    REVOKED = "REVOKED"


class Coverage(BaseModel):
    id: str
    name: str
    limit: float
    deductible: float
    optional: bool = False
    included: bool = True


class QuoteRequest(BaseModel):
    document_type: str = Field(..., json_schema_extra={"example": "CC"})
    document_number: str = Field(..., json_schema_extra={"example": "123456789"})
    birth_date: Union[date, str] = Field(..., json_schema_extra={"example": "1990-05-15"})
    insured_amount: float = Field(..., gt=0, json_schema_extra={"example": 150000000.0})
    city: str = Field(default="Bogota")
    occupation_risk: int = Field(default=1, ge=1, le=5)
    financial_entity: Optional[str] = Field(None, json_schema_extra={"example": "BANCOLOMBIA"})
    term_months: Optional[int] = Field(None, ge=1, le=360, json_schema_extra={"example": 240})
    consent_id: Optional[str] = Field(None, json_schema_extra={"example": "CNS-A1B2C3D4E5F6"})


class PremiumFactor(BaseModel):
    code: str = Field(..., json_schema_extra={"example": "age"})
    label: str = Field(..., json_schema_extra={"example": "Ajuste por edad"})
    multiplier: float = Field(..., description="Multiplicador aplicado; 1.0 en la tarifa base")
    amount: float = Field(..., description="Aporte en pesos a la prima mensual; negativo si la reduce")


class QuoteTierDetail(BaseModel):
    tier: PlanTier
    monthly_premium: float
    annual_premium: float
    coverages: List[Coverage]
    breakdown: List[PremiumFactor] = Field(default_factory=list)


class QuoteResponse(BaseModel):
    quote_id: str
    document_number: str
    status: str = Field(
        ...,
        description="'active'; 'degraded' si el buró no respondió a tiempo; 'review_required' si supera una regla de suscripción",
    )
    degradation_reason: Optional[str] = Field(None, description="'TIMEOUT_EXCEEDED' cuando status es 'degraded'")
    score_applied: float
    tiers: List[QuoteTierDetail]
    created_at: str
    expires_at: str
    underwriting: Optional[UnderwritingDecision] = Field(
        None, description="Motivo y suma máxima aprobable cuando status es 'review_required' (HU-WEB-07)"
    )


class PartnerTokenRequest(BaseModel):
    client_id: str = Field(
        ...,
        min_length=1,
        description="Identificador de cliente OAuth2 del socio de distribución",
        json_schema_extra={"example": "banco_aliado"},
    )
    client_secret: str = Field(
        ...,
        min_length=1,
        description="Secreto del cliente OAuth2 asignado al socio",
        json_schema_extra={"example": "AliadoSecret2026*"},
    )
    grant_type: Literal["client_credentials"] = Field(
        default="client_credentials",
        description="Flujo OAuth2 machine-to-machine para socios B2B",
        json_schema_extra={"example": "client_credentials"},
    )


class PartnerTokenResponse(BaseModel):
    access_token: str = Field(..., description="Token JWT firmado para consumir la API de Quote Edge")
    token_type: str = Field(default="bearer", json_schema_extra={"example": "bearer"})
    expires_in: int = Field(
        ...,
        description="Tiempo de vigencia del token en segundos",
        json_schema_extra={"example": 3600},
    )
    partner_id: str = Field(
        ...,
        description="Identificador único del socio autenticado",
        json_schema_extra={"example": "banco-aliado-1"},
    )


class PartnerQuoteResponse(QuoteResponse):
    partner_id: str = Field(
        ...,
        description="Identificador del socio propietario de la cotización",
        json_schema_extra={"example": "banco-aliado-1"},
    )

    @classmethod
    def from_db(cls, quote: "QuoteDB") -> "PartnerQuoteResponse":
        """Construye la respuesta pública a partir del registro relacional QuoteDB."""
        created_iso = (
            quote.created_at.isoformat()
            if isinstance(quote.created_at, datetime)
            else str(quote.created_at)
        )
        expires_iso = (
            quote.expires_at.isoformat()
            if isinstance(quote.expires_at, datetime)
            else str(quote.expires_at)
        )
        return cls(
            quote_id=quote.quote_id,
            partner_id=quote.partner_id or "",
            document_number=quote.document_number,
            status=quote.status,
            degradation_reason=quote.degradation_reason,
            score_applied=quote.score_applied,
            tiers=[QuoteTierDetail.model_validate(tier) for tier in quote.tiers],
            created_at=created_iso,
            expires_at=expires_iso,
        )


class PartnerQuotaStatus(BaseModel):
    """Estado y telemetría de cuota de un socio B2B dentro de su ventana de tiempo (HU-WEB-12)."""
    partner_id: str
    quota_limit: int
    quota_used: int
    quota_remaining: int
    quota_window_seconds: int
    reset_at: datetime
    retry_after: int = 0


class PartnerQuotaExceededResponse(BaseModel):
    """Respuesta HTTP 429 cuando el socio supera su cuota de cotización contratada (HU-WEB-12)."""
    detail: str = Field(
        default="Se ha superado la cuota de solicitudes contratada para el socio.",
        description="Mensaje claro y comprensible sobre el motivo del rechazo.",
        json_schema_extra={"example": "Se ha superado la cuota de solicitudes contratada para el socio."},
    )
    error: str = Field(
        default="QUOTA_EXCEEDED",
        description="Código de error estandarizado para integración automatizada.",
        json_schema_extra={"example": "QUOTA_EXCEEDED"},
    )
    retry_after: int = Field(
        ...,
        description="Segundos que el socio debe esperar antes de reintentar una nueva solicitud.",
        json_schema_extra={"example": 1500},
    )
    reset_at: str = Field(
        ...,
        description="Fecha y hora UTC ISO-8601 en la que la cuota se renueva.",
        json_schema_extra={"example": "2026-10-31T15:00:00+00:00"},
    )
    quota_limit: int = Field(
        ...,
        description="Límite máximo de solicitudes contratadas para el período.",
        json_schema_extra={"example": 50000},
    )
    quota_remaining: int = Field(
        default=0,
        description="Solicitudes restantes en la ventana activa (0 al ser rechazado).",
        json_schema_extra={"example": 0},
    )


class PartnerDB(Base):
    """Socio de distribución B2B habilitado para cotizar con credenciales propias (HU-WEB-10, HU-WEB-12, RC-23)."""
    __tablename__ = "partners"

    partner_id = Column(String(64), primary_key=True, index=True)
    client_id = Column(String(64), unique=True, nullable=False, index=True)
    client_secret_hash = Column(String(255), nullable=False)
    name = Column(String(120), nullable=False)
    status = Column(String(32), nullable=False, default=PartnerStatus.ACTIVE.value)
    quota_limit = Column(Integer, nullable=False, default=50000)
    quota_used = Column(Integer, nullable=False, default=0)
    quota_window_seconds = Column(Integer, nullable=False, default=3600)
    quota_reset_at = Column(DateTime(timezone=True), nullable=True)
    environment = Column(String(16), nullable=False, default="production")
    contact_email = Column(String(254), nullable=True)
    created_at = Column(DateTime(timezone=True), server_default=func.now(), nullable=False)


class QuoteDB(Base):
    """Cotización calculada, con su traza de degradación y confinamiento opcional por socio (HU-WEB-05, HU-WEB-10, RC-06, RC-08)."""
    __tablename__ = "quotes"

    quote_id = Column(String(16), primary_key=True)
    partner_id = Column(String(64), nullable=True, index=True)
    document_number = Column(String, nullable=False)
    status = Column(String(16), nullable=False)
    degradation_reason = Column(String(32), nullable=True)
    score_applied = Column(Float, nullable=False)
    tiers = Column(JSON, nullable=False)
    created_at = Column(DateTime(timezone=True), nullable=False)
    expires_at = Column(DateTime(timezone=True), nullable=False)
    consent_id = Column(String(36), nullable=True)
    financial_entity = Column(String(120), nullable=True)
    term_months = Column(Integer, nullable=True)
    birth_date = Column(DateTime(timezone=True), nullable=True)
