"""Decisión de suscripción y registro inmutable de rechazos (HU-WEB-07, HA-17)."""
from typing import Literal

from pydantic import BaseModel, Field
from sqlalchemy import DDL, Column, DateTime, Float, String, event

from app.core.database import Base


class UnderwritingDecision(BaseModel):
    decision: Literal["REVIEW_REQUIRED"] = "REVIEW_REQUIRED"
    rule_id: str = Field(..., description="Regla aplicada, para auditoría; el portal no la muestra")
    reason: str = Field(..., description="Motivo en lenguaje de negocio")
    max_approvable_amount: float = Field(..., description="Suma asegurada máxima aprobable en COP")


class UnderwritingRejectionDB(Base):
    """Rechazo de suscripción; no guarda el puntaje del buró. La base impide cambiarlo o borrarlo."""
    __tablename__ = "underwriting_rejections"

    rejection_id = Column(String(16), primary_key=True)
    document_number = Column(String, nullable=False, index=True)
    rule_id = Column(String(8), nullable=False)
    reason = Column(String, nullable=False)
    requested_amount = Column(Float, nullable=False)
    max_approvable_amount = Column(Float, nullable=False)
    created_at = Column(DateTime(timezone=True), nullable=False)


# asyncpg ejecuta una sentencia por llamada: la función y el disparador van por separado.
_IMMUTABLE = (
    """
    CREATE OR REPLACE FUNCTION underwriting_rejections_immutable() RETURNS trigger AS $$
    BEGIN
        RAISE EXCEPTION 'Los rechazos de suscripción no se pueden modificar ni borrar';
    END;
    $$ LANGUAGE plpgsql
    """,
    """
    CREATE TRIGGER underwriting_rejections_no_change
        BEFORE UPDATE OR DELETE ON underwriting_rejections
        FOR EACH ROW EXECUTE FUNCTION underwriting_rejections_immutable()
    """,
)
for _statement in _IMMUTABLE:
    event.listen(UnderwritingRejectionDB.__table__, "after_create", DDL(_statement).execute_if(dialect="postgresql"))
