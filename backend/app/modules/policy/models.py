"""Modelos de datos para la emisión de pólizas."""
from pydantic import BaseModel, ConfigDict, Field
from typing import List, Literal, Optional
from datetime import datetime
from sqlalchemy import JSON, Column, DateTime, ForeignKey, Sequence, String
from app.core.database import Base
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


class PolicyHolder(BaseModel):
    """Datos del titular del paso 1 del cotizador; el número de documento sale de la cotización."""
    name: str = Field(..., min_length=2, max_length=150)
    document_type: str = Field(..., min_length=2, max_length=8)
    email: str = Field(..., max_length=254, pattern=r"^[^@\s]+@[^@\s]+\.[^@\s]+$")
    phone: str = Field(..., min_length=7, max_length=20, pattern=r"^\+?[0-9 ]{7,20}$")


class PolicyIssueRequest(BaseModel):
    quote_id: str
    tier: PlanTier
    beneficiaries: List[dict] = Field(default_factory=list)
    holder: Optional[PolicyHolder] = None


PolicyStatus = Literal["ISSUED", "ACTIVE", "CANCELLED"]


class PolicySearchItem(BaseModel):
    """Fila de la búsqueda de operaciones: documento enmascarado y sin datos de contacto (HU-WEB-08)."""
    policy_number: str
    holder_name: Optional[str]
    holder_document: str
    plan: PlanTier
    status: str
    valid_from: datetime
    valid_until: datetime
    partner_name: Optional[str]


class PolicySearchResponse(BaseModel):
    items: List[PolicySearchItem]
    has_more: bool


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
