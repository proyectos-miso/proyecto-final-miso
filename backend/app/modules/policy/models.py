"""Modelos de datos para la emisión de pólizas."""
from pydantic import BaseModel, ConfigDict, Field, field_validator
from typing import List, Literal, Optional
from datetime import datetime
from sqlalchemy import JSON, Column, DateTime, Float, ForeignKey, Integer, Sequence, String, Text
from app.core.database import Base
from app.core.immutable import make_immutable
from app.modules.claims.models import ClaimSummary
from app.modules.rating.models import PlanTier, QuoteTierDetail

# Consecutivo global del número POL-YYYY-XXXXX; los saltos por emisiones repetidas no importan.
POLICY_NUMBER_SEQ = Sequence("policy_number_seq", metadata=Base.metadata)


class PolicyDB(Base):
    """Póliza emitida; copia el plan cotizado para que sus condiciones no cambien después (HA-16)."""
    __tablename__ = "policies"

    policy_number = Column(String(16), primary_key=True)
    quote_id = Column(String(16), ForeignKey("quotes.quote_id"), unique=True, nullable=False)
    partner_id = Column(String, ForeignKey("partners.partner_id"), nullable=True, index=True)
    insured_document = Column(String, nullable=False, index=True)
    beneficiaries = Column(JSON, nullable=False)
    conditions = Column(JSON, nullable=False)
    status = Column(String(16), nullable=False, default="ISSUED")
    issued_at = Column(DateTime(timezone=True), nullable=False)
    valid_until = Column(DateTime(timezone=True), nullable=False)
    # Titular capturado en el portal al emitir (HU-WEB-08); las pólizas B2B no lo traen.
    holder_name = Column(String(150), nullable=True)
    holder_document_type = Column(String(8), nullable=True)
    holder_email = Column(String(254), nullable=True)
    holder_phone = Column(String(20), nullable=True)
    # Carátula guardada en S3 al primer pedido (HU-WEB-16).
    pdf_s3_key = Column(String(64), nullable=True)
    # Sube con cada endoso; la carátula nueva lleva su versión (HU-WEB-15).
    version = Column(Integer, nullable=False, default=1, server_default="1")


class PolicyEventDB(Base):
    """Operaciones sobre la póliza (endosos, cancelación, terminación); no se cambian ni se borran (HA-17)."""
    __tablename__ = "policy_events"

    id = Column(Integer, primary_key=True, autoincrement=True)
    policy_number = Column(String(16), ForeignKey("policies.policy_number"), nullable=False, index=True)
    kind = Column(String(32), nullable=False)
    version = Column(Integer, nullable=False)
    actor = Column(String(120), nullable=False)
    justification = Column(Text, nullable=False)
    before = Column(JSON, nullable=True)
    after = Column(JSON, nullable=True)
    amount = Column(Float, nullable=True)
    created_at = Column(DateTime(timezone=True), nullable=False)


make_immutable(PolicyEventDB.__table__, "Las operaciones de una póliza no se pueden modificar ni borrar")


class PolicyHolder(BaseModel):
    """Datos del titular del paso 1 del cotizador; el número de documento sale de la cotización."""
    name: str = Field(..., min_length=2, max_length=150)
    document_type: str = Field(..., min_length=2, max_length=8)
    email: str = Field(..., max_length=254, pattern=r"^[^@\s]+@[^@\s]+\.[^@\s]+$")
    phone: str = Field(..., min_length=7, max_length=20, pattern=r"^\+?[0-9 ]{7,20}$")


Relationship = Literal["CONYUGE", "HIJO", "PADRE", "MADRE", "HERMANO", "OTRO"]


class Beneficiary(BaseModel):
    """Beneficiario de una póliza de vida; los porcentajes de todos suman 100 (HU-WEB-15, RC-01)."""
    name: str = Field(..., min_length=2, max_length=150)
    document_type: str = Field(..., min_length=2, max_length=8)
    document_number: str = Field(..., pattern=r"^[0-9]{5,15}$")
    relationship: Relationship
    percentage: float = Field(..., gt=0, le=100)


def check_beneficiaries(beneficiaries: List[Beneficiary]) -> List[Beneficiary]:
    if abs(sum(b.percentage for b in beneficiaries) - 100) > 0.001:
        raise ValueError("Los porcentajes de los beneficiarios deben sumar 100.")
    if len({b.document_number for b in beneficiaries}) != len(beneficiaries):
        raise ValueError("Un beneficiario no puede repetirse.")
    return beneficiaries


class PolicyIssueRequest(BaseModel):
    quote_id: str
    tier: PlanTier
    beneficiaries: List[Beneficiary] = Field(..., min_length=1, max_length=10)
    holder: Optional[PolicyHolder] = None

    @field_validator("beneficiaries")
    @classmethod
    def _sum_100(cls, value: List[Beneficiary]) -> List[Beneficiary]:
        return check_beneficiaries(value)


PolicyStatus = Literal["ISSUED", "ACTIVE", "CANCELLED", "TERMINATED", "EXPIRED"]


class PolicySearchItem(BaseModel):
    """Fila de la búsqueda de operaciones: documento enmascarado y sin datos de contacto (HU-WEB-08)."""
    policy_number: str
    holder_name: Optional[str] = None
    holder_document: str
    plan: PlanTier
    status: str
    valid_from: datetime
    valid_until: datetime
    partner_name: Optional[str] = None


class PolicySearchResponse(BaseModel):
    items: List[PolicySearchItem]
    has_more: bool


class PolicyDetailHolder(BaseModel):
    """Titular con documento, correo y teléfono enmascarados (HU-WEB-09)."""
    name: Optional[str] = None
    document_type: Optional[str] = None
    document: str
    email: Optional[str] = None
    phone: Optional[str] = None


class PolicyDetailCoverage(BaseModel):
    name: str
    limit: float
    deductible: float


class PolicyPayment(BaseModel):
    """Resultado de un cobro; nunca el token, la cuenta ni la clave de idempotencia."""
    paid_at: datetime
    amount: float
    currency: str
    method: str
    status: str
    receipt_number: Optional[str] = None
    decline_reason: Optional[str] = None


class MaskedBeneficiary(BaseModel):
    name: str
    document_type: str
    document: str
    relationship: str
    percentage: float


class PolicyDetail(BaseModel):
    """Detalle de operaciones: titular y beneficiarios con el documento enmascarado (HU-WEB-09, HU-WEB-15)."""
    policy_number: str
    version: int = 1
    status: PolicyStatus
    plan: PlanTier
    valid_from: datetime
    valid_until: datetime
    partner_name: Optional[str] = None
    holder: PolicyDetailHolder
    insured_amount: float
    monthly_premium: float
    annual_premium: float
    coverages: List[PolicyDetailCoverage]
    payments: List[PolicyPayment]
    claims: List[ClaimSummary]
    beneficiaries: List[MaskedBeneficiary] = Field(default_factory=list)


class EndorsementRequest(BaseModel):
    """Endoso de beneficiarios o de suma asegurada; la suma solo baja porque no se vuelve a suscribir (RC-21)."""
    kind: Literal["BENEFICIARIES", "SUM_INSURED"]
    justification: str = Field(..., min_length=20, max_length=2000)
    beneficiaries: Optional[List[Beneficiary]] = Field(None, min_length=1, max_length=10)
    insured_amount: Optional[float] = Field(None, gt=0)

    @field_validator("beneficiaries")
    @classmethod
    def _sum_100(cls, value: Optional[List[Beneficiary]]) -> Optional[List[Beneficiary]]:
        return check_beneficiaries(value) if value is not None else value


class CancellationRequest(BaseModel):
    reason: str = Field(..., min_length=20, max_length=2000)


class PolicyOperationResult(BaseModel):
    policy_number: str
    status: PolicyStatus
    version: int
    refund_amount: Optional[float] = None
    valid_until: Optional[datetime] = None


class AuditEntry(BaseModel):
    """Una decisión sobre la póliza, en el orden en que ocurrió (RC-19)."""
    occurred_at: datetime
    kind: str
    actor: str
    summary: str
    justification: Optional[str] = None
    amount: Optional[float] = None
    reference: Optional[str] = None


class PolicyAudit(BaseModel):
    policy_number: str
    entries: List[AuditEntry]


class CaratulaLink(BaseModel):
    """Enlace prefirmado de la carátula; da acceso al PDF hasta que vence (HU-WEB-16)."""
    url: str
    expires_at: datetime


class PolicyRecord(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    policy_number: str
    quote_id: str
    partner_id: Optional[str] = None
    insured_document: str
    conditions: QuoteTierDetail
    status: str = Field(default="ISSUED")
    issued_at: datetime
    valid_until: datetime
    pdf_s3_key: Optional[str] = Field(None, description="Carátula en S3; llega con HU-WEB-16")


class PartnerPolicyIssueRequest(BaseModel):
    """Solicitud de emisión B2B desde el canal del socio (HU-WEB-14)."""
    quote_id: str = Field(..., description="Identificador de la cotización calculada por el socio")
    tier: PlanTier = Field(..., description="Nivel de plan seleccionado para la emisión")
    beneficiaries: List[dict] = Field(default_factory=list, description="Beneficiarios designados por el asegurado")


class PartnerPolicyResponse(BaseModel):
    """Proyección de lectura CQRS de la póliza emitida confinada al socio B2B (HU-WEB-14, HA-04, HA-14)."""
    model_config = ConfigDict(from_attributes=True)

    policy_number: str = Field(..., description="Número oficial de póliza POL-YYYY-XXXXX")
    quote_id: str = Field(..., description="ID de la cotización origen")
    partner_id: str = Field(..., description="ID del socio B2B propietario de la emisión")
    insured_document: str = Field(..., description="Número de documento del asegurado")
    conditions: QuoteTierDetail = Field(..., description="Condiciones del plan contratado")
    status: str = Field(default="ISSUED", description="Estado de la póliza (ISSUED, ACTIVE, CANCELLED)")
    issued_at: datetime = Field(..., description="Fecha y hora de emisión")
    valid_until: datetime = Field(..., description="Fecha y hora de fin de vigencia (1 año)")
    pdf_s3_key: Optional[str] = Field(None, description="Clave S3 del certificado PDF (HU-WEB-16)")


class CardCoverage(BaseModel):
    name: str
    limit: float


class InsuredPolicyCard(BaseModel):
    """Tarjeta de la billetera: sin documento, beneficiarios ni prima (HU-MOV-04)."""
    policy_number: str
    plan: PlanTier
    insured_amount: float
    holder_name: str
    coverages: List[CardCoverage]
    valid_from: datetime
    valid_until: datetime


class InsuredPolicies(BaseModel):
    policies: List[InsuredPolicyCard]
