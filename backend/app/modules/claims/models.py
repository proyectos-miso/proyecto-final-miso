"""Siniestros de una póliza: tabla mínima que lee el detalle (HU-WEB-09) y que amplía el reporte (HU-MOV-10)."""
from datetime import datetime

from pydantic import BaseModel
from sqlalchemy import Column, DateTime, ForeignKey, String

from app.core.database import Base


class ClaimDB(Base):
    __tablename__ = "claims"

    claim_number = Column(String(20), primary_key=True)
    policy_number = Column(String(16), ForeignKey("policies.policy_number"), nullable=False, index=True)
    status = Column(String(16), nullable=False)
    reported_at = Column(DateTime(timezone=True), nullable=False)


class ClaimSummary(BaseModel):
    """Lo que el detalle muestra del siniestro: sin descripción ni ubicación, que pueden traer datos de salud."""

    claim_number: str
    status: str
    reported_at: datetime
