"""Siniestros: expediente, fotos, historial inmutable y peritos (HU-WEB-09, HU-WEB-15).
HU-MOV-09 llena estas tablas al recibir el reporte y HU-MOV-10 le da el radicado."""
from datetime import datetime
from typing import List, Literal, Optional

from pydantic import BaseModel, Field
from sqlalchemy import JSON, Boolean, Column, DateTime, Float, ForeignKey, Integer, String, Text

from app.core.database import Base
from app.core.immutable import make_immutable

ClaimStatus = Literal["REPORTED", "IN_REVIEW", "REJECTED", "SETTLED", "PAID"]
ClaimOrigin = Literal["APP", "PARAMETRIC"]
OPEN_STATUSES = ("REPORTED", "IN_REVIEW")


class ClaimDB(Base):
    __tablename__ = "claims"

    claim_number = Column(String(20), primary_key=True)
    policy_number = Column(String(16), ForeignKey("policies.policy_number"), nullable=False, index=True)
    status = Column(String(16), nullable=False)
    reported_at = Column(DateTime(timezone=True), nullable=False)
    # HU-WEB-15: lo que reporta el asegurado y lo que decide operaciones.
    origin = Column(String(16), nullable=False, default="APP", server_default="APP")
    coverage_id = Column(String(32), nullable=True)
    description = Column(Text, nullable=True)
    parametric_event = Column(JSON, nullable=True)
    assigned_to = Column(String(120), nullable=True)
    expert_id = Column(String(32), ForeignKey("experts.expert_id"), nullable=True)
    settled_amount = Column(Float, nullable=True)


class ClaimPhotoDB(Base):
    """Foto del reporte: la huella SHA-256 es la que calculó el teléfono al tomarla (ADR 0024)."""
    __tablename__ = "claim_photos"

    photo_id = Column(String(36), primary_key=True)
    claim_number = Column(String(20), ForeignKey("claims.claim_number"), nullable=False, index=True)
    position = Column(Integer, nullable=False)
    s3_key = Column(String(128), nullable=False)
    sha256 = Column(String(64), nullable=False)
    latitude = Column(Float, nullable=False)
    longitude = Column(Float, nullable=False)
    accuracy_m = Column(Float, nullable=False)
    mock_location = Column(Boolean, nullable=False, default=False)
    captured_at = Column(DateTime(timezone=True), nullable=False)
    trusted_time_source = Column(String(16), nullable=True)


class ClaimEventDB(Base):
    """Historial del siniestro; un dictamen registrado no se puede cambiar ni borrar (HA-17, HA-18)."""
    __tablename__ = "claim_events"

    id = Column(Integer, primary_key=True, autoincrement=True)
    claim_number = Column(String(20), ForeignKey("claims.claim_number"), nullable=False, index=True)
    kind = Column(String(24), nullable=False)
    actor = Column(String(120), nullable=False)
    justification = Column(Text, nullable=True)
    amount = Column(Float, nullable=True)
    detail = Column(JSON, nullable=True)
    created_at = Column(DateTime(timezone=True), nullable=False)


make_immutable(ClaimEventDB.__table__, "El historial de un siniestro no se puede modificar ni borrar")


class ExpertDB(Base):
    """Catálogo de peritos para la inspección en sitio; no hay integración con sistemas externos."""
    __tablename__ = "experts"

    expert_id = Column(String(32), primary_key=True)
    name = Column(String(120), nullable=False)
    city = Column(String(80), nullable=False, index=True)


class ClaimSummary(BaseModel):
    """Lo que el detalle de la póliza muestra del siniestro: sin descripción ni ubicación (HU-WEB-09)."""

    claim_number: str
    status: str
    reported_at: datetime


class ClaimListItem(BaseModel):
    claim_number: str
    policy_number: str
    reported_at: datetime
    status: ClaimStatus
    origin: ClaimOrigin
    assigned_to: Optional[str] = None


class ClaimList(BaseModel):
    items: List[ClaimListItem]
    has_more: bool


class ClaimPhoto(BaseModel):
    photo_id: str
    position: int
    url: Optional[str] = None
    latitude: float
    longitude: float
    accuracy_m: float
    mock_location: bool
    captured_at: datetime
    trusted_time: bool
    integrity: Literal["INTACT", "ALTERED", "UNVERIFIED"]


class ClaimEvent(BaseModel):
    kind: str
    actor: str
    justification: Optional[str] = None
    amount: Optional[float] = None
    created_at: datetime


class ClaimPayout(BaseModel):
    beneficiary_name: str
    beneficiary_document: str
    percentage: float
    amount: float
    status: str
    receipt_number: Optional[str] = None


class Expert(BaseModel):
    expert_id: str
    name: str
    city: str


class ClaimCoverage(BaseModel):
    coverage_id: str
    name: str
    limit: float


class ClaimDetail(BaseModel):
    """Expediente: datos del asegurado enmascarados y fotos con su verificación de huella."""
    claim_number: str
    status: ClaimStatus
    origin: ClaimOrigin
    reported_at: datetime
    description: Optional[str] = None
    parametric_event: Optional[dict] = None
    policy_number: str
    policy_status: str
    plan: str
    insured_name: Optional[str] = None
    insured_document: str
    insured_phone: Optional[str] = None
    coverage: Optional[ClaimCoverage] = None
    coverages: List[ClaimCoverage]
    assigned_to: Optional[str] = None
    expert: Optional[Expert] = None
    settled_amount: Optional[float] = None
    photos: List[ClaimPhoto]
    events: List[ClaimEvent]
    payouts: List[ClaimPayout]


class ClaimAssignRequest(BaseModel):
    expert_id: Optional[str] = Field(None, max_length=32)


class ClaimDecisionRequest(BaseModel):
    decision: Literal["APPROVE", "REJECT"]
    justification: str = Field(..., min_length=20, max_length=2000)
    coverage_id: Optional[str] = Field(None, max_length=32)
    amount: Optional[float] = Field(None, gt=0)
