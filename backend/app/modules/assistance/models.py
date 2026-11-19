"""Prestadores y solicitudes de asistencia (mejora FE-MOV-07). La ubicación del asegurado no se guarda: solo la
distancia al prestador sugerido, para explicar el tiempo estimado."""
from datetime import datetime
from typing import List, Literal, Optional

from pydantic import BaseModel, ConfigDict, Field, model_validator
from sqlalchemy import JSON, Boolean, Column, DateTime, Float, ForeignKey, Integer, String

from app.core.database import Base

Service = Literal["GRUA", "ASISTENCIA_VIAL", "MECANICA", "CERRAJERIA", "PLOMERIA", "ATENCION_MEDICA"]
POLICY_NUMBER = r"^POL-\d{4}-\d{5}$"
CITY_CODE = r"^[A-Z_]{2,64}$"


class ProviderDB(Base):
    __tablename__ = "providers"

    provider_id = Column(String(16), primary_key=True)
    name = Column(String(120), nullable=False)
    kind = Column(String(20), nullable=False)
    services = Column(JSON, nullable=False)
    city_code = Column(String(64), ForeignKey("cities.code"), nullable=False, index=True)
    address = Column(String(160), nullable=False)
    phone = Column(String(20), nullable=False)
    lat = Column(Float, nullable=False)
    lng = Column(Float, nullable=False)
    active = Column(Boolean, nullable=False, default=True, server_default="true")


class AssistanceRequestDB(Base):
    __tablename__ = "assistance_requests"

    request_id = Column(String(36), primary_key=True)
    user_id = Column(String(36), ForeignKey("insured_accounts.user_id", ondelete="CASCADE"), nullable=False, index=True)
    policy_number = Column(String(16), ForeignKey("policies.policy_number"), nullable=False)
    service = Column(String(20), nullable=False)
    provider_id = Column(String(16), ForeignKey("providers.provider_id"), nullable=False)
    city_code = Column(String(64), nullable=True)
    description = Column(String(300), nullable=False, default="")
    distance_km = Column(Float, nullable=True)
    eta_minutes = Column(Integer, nullable=False)
    status = Column(String(12), nullable=False)
    created_at = Column(DateTime(timezone=True), nullable=False)


class Provider(BaseModel):
    provider_id: str
    name: str
    kind: str
    services: List[str]
    city_code: str
    address: str
    phone: str
    lat: float
    lng: float
    distance_km: Optional[float] = None


class Providers(BaseModel):
    providers: List[Provider]


def location_or_city(lat: Optional[float], lng: Optional[float], city: Optional[str]) -> None:
    if (lat is None) != (lng is None):
        raise ValueError("La ubicación necesita latitud y longitud.")
    if lat is None and city is None:
        raise ValueError("Indique su ubicación o una ciudad.")


class ProviderSearch(BaseModel):
    """Búsqueda de prestadores; va en el cuerpo de un POST para que la ubicación no quede en la URL."""

    model_config = ConfigDict(extra="forbid")

    lat: Optional[float] = Field(None, ge=-90, le=90)
    lng: Optional[float] = Field(None, ge=-180, le=180)
    city: Optional[str] = Field(None, pattern=CITY_CODE)
    service: Optional[Service] = None

    @model_validator(mode="after")
    def location_or_city(self) -> "ProviderSearch":
        location_or_city(self.lat, self.lng, self.city)
        return self


class AssistanceRequest(BaseModel):
    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)

    policy_number: str = Field(..., pattern=POLICY_NUMBER)
    service: Service
    lat: Optional[float] = Field(None, ge=-90, le=90)
    lng: Optional[float] = Field(None, ge=-180, le=180)
    city: Optional[str] = Field(None, pattern=CITY_CODE)
    description: str = Field("", max_length=300)

    @model_validator(mode="after")
    def location_or_city(self) -> "AssistanceRequest":
        location_or_city(self.lat, self.lng, self.city)
        return self


class AssistanceReceipt(BaseModel):
    """Constancia de la solicitud: `provider` es el prestador sugerido y `eta_minutes` un tiempo estimado si el
    asegurado lo contacta; nadie lo despacha todavía (RISK-05)."""

    request_id: str
    policy_number: str
    service: str
    status: str
    provider: Provider
    eta_minutes: int
    created_at: datetime


class AssistanceRequests(BaseModel):
    requests: List[AssistanceReceipt]
