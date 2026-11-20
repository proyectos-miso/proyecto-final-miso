"""Modelos de datos para cobro y transacciones financieras."""
from datetime import datetime
from pydantic import BaseModel, ConfigDict, Field
from typing import Literal
from sqlalchemy import Column, DateTime, Float, ForeignKey, Sequence, String, UniqueConstraint
from app.core.database import Base

# Consecutivo global del recibo REC-YYYY-XXXXX.
RECEIPT_NUMBER_SEQ = Sequence("receipt_number_seq", metadata=Base.metadata)


class PaymentTransactionDB(Base):
    """Intento de cobro de una póliza, aprobado o rechazado; no guarda el token del medio de pago (HA-15)."""
    __tablename__ = "payment_transactions"
    __table_args__ = (UniqueConstraint("partner_id", "idempotency_key"),)

    transaction_id = Column(String(36), primary_key=True)
    partner_id = Column(String(64), nullable=False)
    idempotency_key = Column(String(128), nullable=False)
    policy_number = Column(String(16), ForeignKey("policies.policy_number"), nullable=False)
    amount = Column(Float, nullable=False)
    currency = Column(String(3), nullable=False)
    payment_method = Column(String(16), nullable=False)
    status = Column(String(16), nullable=False)
    decline_reason = Column(String, nullable=True)
    receipt_number = Column(String(16), unique=True, nullable=True)
    created_at = Column(DateTime(timezone=True), nullable=False)


class PaymentChargeRequest(BaseModel):
    policy_number: str
    amount: float = Field(..., gt=0, description="Primera prima mensual del plan emitido")
    currency: str = Field(default="COP")
    payment_method: Literal["PSE", "CARD"]
    token_or_account: str = Field(
        ..., pattern=r"^tok_\w+$", description="Token de la pasarela; nunca el número de la tarjeta (HA-15)",
        json_schema_extra={"example": "tok_pse_bancolombia"},
    )


class PaymentReceipt(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    transaction_id: str
    idempotency_key: str
    policy_number: str
    amount: float
    status: str = Field(default="APPROVED")
    receipt_number: str
    created_at: datetime


class PayoutDB(Base):
    """Pago que Solventa hace: indemnización a un beneficiario o devolución de prima (HU-WEB-15).
    Uno solo por referencia, tipo y documento, aunque se reintente (HA-11)."""
    __tablename__ = "payouts"
    __table_args__ = (UniqueConstraint("reference", "kind", "beneficiary_document"),)

    payout_id = Column(String(36), primary_key=True)
    reference = Column(String(20), nullable=False, index=True)
    kind = Column(String(16), nullable=False)
    policy_number = Column(String(16), ForeignKey("policies.policy_number"), nullable=False, index=True)
    beneficiary_name = Column(String(150), nullable=False)
    beneficiary_document = Column(String(20), nullable=False)
    percentage = Column(Float, nullable=False)
    amount = Column(Float, nullable=False)
    status = Column(String(16), nullable=False)
    decline_reason = Column(String, nullable=True)
    receipt_number = Column(String(16), unique=True, nullable=True)
    created_at = Column(DateTime(timezone=True), nullable=False)
