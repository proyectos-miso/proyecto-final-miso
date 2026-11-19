"""Modelos de identidad del asegurado para el canal móvil."""
from datetime import datetime
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator


class OtpRequest(BaseModel):
    email: str = Field(..., json_schema_extra={"example": "maria.ruiz@correo.co"})


class OtpResponse(BaseModel):
    message: str
    expires_in: int
    debug_code: str | None = Field(default=None, description="Solo con SMS_PROVIDER=console: simula el SMS")


class InsuredLoginRequest(BaseModel):
    email: str = Field(..., json_schema_extra={"example": "maria.ruiz@correo.co"})
    password: str = Field(..., json_schema_extra={"example": "Solventa2026!"})
    otp_code: str = Field(..., min_length=6, max_length=6, json_schema_extra={"example": "417902"})


class TokenPair(BaseModel):
    access_token: str
    refresh_token: str
    report_token: str | None = None
    token_type: str = "bearer"
    expires_in: int
    full_name: str


class RefreshTokenRequest(BaseModel):
    refresh_token: str


class LogoutRequest(RefreshTokenRequest):
    # La app lo envía al cerrarse sin cerrar sesión: el token de reporte sigue sirviendo (HU-MOV-09).
    keep_reporting: bool = False


class DeviceRegistrationResponse(BaseModel):
    device_id: str
    device_secret: str


class BiometricLoginRequest(BaseModel):
    device_id: str = Field(..., min_length=1, max_length=36)
    device_secret: str = Field(..., min_length=1, max_length=128)


class PasswordForgotRequest(BaseModel):
    email: str


class PasswordResetRequest(BaseModel):
    email: str
    code: str = Field(..., min_length=6, max_length=6)
    new_password: str = Field(..., min_length=1, max_length=128)


class MessageResponse(BaseModel):
    message: str


class LockedResponse(BaseModel):
    detail: str
    locked_until: datetime
    retry_after: int


# Registro desde la app con documento y selfie (mejora FE-MOV-01).
DOCUMENT_NUMBER = r"^[0-9A-Z]{5,12}$"
MOBILE_PHONE = r"^(\+57)?3\d{9}$"


class _DocumentFields(BaseModel):
    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)

    document_type: Literal["CC", "CE", "PA"]
    document_number: str = Field(..., pattern=DOCUMENT_NUMBER, json_schema_extra={"example": "1098765432"})
    phone: str = Field(..., pattern=MOBILE_PHONE, json_schema_extra={"example": "3000000000"})

    @field_validator("phone")
    @classmethod
    def with_country_code(cls, phone: str) -> str:
        return phone if phone.startswith("+57") else f"+57{phone}"

    @model_validator(mode="after")
    def numeric_cc(self):
        if self.document_type == "CC" and not self.document_number.isdigit():
            raise ValueError("La cédula de ciudadanía solo lleva números.")
        return self


class RegistrationOtpRequest(_DocumentFields):
    pass


class RegistrationData(_DocumentFields):
    email: str = Field(..., min_length=6, max_length=254, pattern=r"^[^@\s]+@[^@\s]+\.[^@\s]+$")
    full_name: str = Field(..., min_length=3, max_length=150)
    password: str = Field(..., min_length=1, max_length=128)
    otp_code: str = Field(..., pattern=r"^\d{6}$")
    terms_accepted: Literal[True]
    biometric_consent_accepted: Literal[True]


class RegistrationResult(TokenPair):
    verification_status: Literal["APPROVED", "PENDING"]
