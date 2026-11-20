"""Router BFF de autenticación del asegurado para la app móvil (HU-MOV-02, HU-MOV-12)."""
from typing import Any, Dict, Optional

from fastapi import APIRouter, Depends, File, Form, HTTPException, Request, Response, UploadFile, status
from fastapi.responses import JSONResponse
from pydantic import ValidationError
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import settings
from app.core.database import get_db_session
from app.core.security import require_roles
from app.core.storage import DocumentStorage, StorageUnavailableError, get_document_storage
from app.modules.claims.intake import JPEG_MAGIC, MAX_PHOTO_BYTES
from app.modules.identity import policy
from app.modules.identity.adapters import get_identity_verifier
from app.modules.identity.ports import APPROVED, IdentityVerifier
from app.modules.identity.models import (
    BiometricLoginRequest,
    DeviceRegistrationResponse,
    InsuredLoginRequest,
    LockedResponse,
    MessageResponse,
    OtpRequest,
    OtpResponse,
    PasswordForgotRequest,
    PasswordResetRequest,
    LogoutRequest,
    RefreshTokenRequest,
    RegistrationData,
    RegistrationOtpRequest,
    RegistrationResult,
    TokenPair,
)
from app.modules.identity.service import (
    OTP_TTL,
    AccountLockedError,
    InvalidCredentialsError,
    InvalidTokenError,
    RateLimitedError,
    SamePasswordError,
    SmsUnavailableError,
    WeakPasswordError,
    insured_auth_service,
)
from app.modules.identity.service_registration import (
    DuplicateAccountError,
    IdentityPhotos,
    IdentityRejectedError,
    InvalidRegistrationCodeError,
    NewInsured,
    RegistrationOutcome,
    insured_registration_service,
)

router = APIRouter(prefix="/auth", tags=["Auth Asegurado"])

require_insured = require_roles(["insured"])

INVALID_CREDENTIALS = "Los datos ingresados no son válidos."
CODE_SENT = "Si el correo está registrado, enviamos un código por SMS."
REGISTRATION_CODE_SENT = "Enviamos un código por SMS al celular indicado."
WEAK_PASSWORD = f"La contraseña debe tener al menos {policy.MIN_PASSWORD_LENGTH} caracteres, con letras y números."
LOCKED_RESPONSES: Dict[int | str, Dict[str, Any]] = {423: {"model": LockedResponse}}


def _client_ip(request: Request) -> str | None:
    return policy.client_ip(request.headers, request.client.host if request.client else None)


def _locked(exc: AccountLockedError) -> JSONResponse:
    return JSONResponse(
        status_code=status.HTTP_423_LOCKED,
        headers={"Retry-After": str(exc.retry_after)},
        content={
            "detail": "Por seguridad, el ingreso está suspendido temporalmente.",
            "locked_until": exc.locked_until.isoformat(),
            "retry_after": exc.retry_after,
        },
    )


def _rate_limited(exc: RateLimitedError) -> HTTPException:
    return HTTPException(
        status_code=status.HTTP_429_TOO_MANY_REQUESTS,
        detail="Demasiadas solicitudes. Espere antes de intentar de nuevo.",
        headers={"Retry-After": str(exc.retry_after)},
    )


def _sms_unavailable() -> HTTPException:
    return HTTPException(
        status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
        detail="No pudimos enviar el código. Intente en unos minutos.",
    )


def _otp_response(code: str | None) -> OtpResponse:
    expose = insured_auth_service.sms.exposes_code and settings.debug_enabled
    return OtpResponse(
        message=CODE_SENT,
        expires_in=int(OTP_TTL.total_seconds()),
        debug_code=code if expose else None,
    )


@router.post("/otp", response_model=OtpResponse)
async def request_otp(request: OtpRequest, http: Request, db: AsyncSession = Depends(get_db_session)):
    try:
        code = await insured_auth_service.request_otp(db, request.email, _client_ip(http))
    except RateLimitedError as exc:
        raise _rate_limited(exc)
    except SmsUnavailableError:
        raise _sms_unavailable()
    return _otp_response(code)


@router.post("/login", response_model=TokenPair, responses=LOCKED_RESPONSES)
async def login(request: InsuredLoginRequest, http: Request, db: AsyncSession = Depends(get_db_session)):
    try:
        return await insured_auth_service.login(
            db, request.email, request.password, request.otp_code, _client_ip(http)
        )
    except AccountLockedError as exc:
        return _locked(exc)
    except RateLimitedError as exc:
        raise _rate_limited(exc)
    except InvalidCredentialsError:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail=INVALID_CREDENTIALS)


@router.post("/logout", status_code=status.HTTP_204_NO_CONTENT)
async def logout(request: LogoutRequest, db: AsyncSession = Depends(get_db_session)):
    try:
        await insured_auth_service.logout(db, request.refresh_token, request.keep_reporting)
    except InvalidTokenError:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Token de sesión inválido.")


@router.post("/refresh", response_model=TokenPair, responses=LOCKED_RESPONSES)
async def refresh(request: RefreshTokenRequest, db: AsyncSession = Depends(get_db_session)):
    try:
        return await insured_auth_service.refresh(db, request.refresh_token)
    except AccountLockedError as exc:
        return _locked(exc)
    except InvalidTokenError:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Sesión revocada o vencida.")


@router.post("/devices", response_model=DeviceRegistrationResponse, status_code=status.HTTP_201_CREATED)
async def register_device(
    claims: Dict[str, Any] = Depends(require_insured), db: AsyncSession = Depends(get_db_session)
):
    try:
        device_id, secret = await insured_auth_service.register_device(db, claims["sub"])
    except InvalidTokenError:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Sesión inválida.")
    return DeviceRegistrationResponse(device_id=device_id, device_secret=secret)


@router.delete("/devices/{device_id}", status_code=status.HTTP_204_NO_CONTENT)
async def revoke_device(
    device_id: str, claims: Dict[str, Any] = Depends(require_insured), db: AsyncSession = Depends(get_db_session)
):
    await insured_auth_service.revoke_device(db, claims["sub"], device_id)


@router.post("/biometric", response_model=TokenPair, responses=LOCKED_RESPONSES)
async def biometric_login(request: BiometricLoginRequest, http: Request, db: AsyncSession = Depends(get_db_session)):
    try:
        return await insured_auth_service.biometric_login(
            db, request.device_id, request.device_secret, _client_ip(http)
        )
    except AccountLockedError as exc:
        return _locked(exc)
    except RateLimitedError as exc:
        raise _rate_limited(exc)
    except InvalidCredentialsError:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="El ingreso con huella ya no es válido en este teléfono.",
        )


@router.post("/password/forgot", response_model=OtpResponse)
async def forgot_password(request: PasswordForgotRequest, http: Request, db: AsyncSession = Depends(get_db_session)):
    try:
        code = await insured_auth_service.forgot_password(db, request.email, _client_ip(http))
    except RateLimitedError as exc:
        raise _rate_limited(exc)
    except SmsUnavailableError:
        raise _sms_unavailable()
    return _otp_response(code)


@router.post("/password/reset", response_model=MessageResponse)
async def reset_password(request: PasswordResetRequest, http: Request, db: AsyncSession = Depends(get_db_session)):
    try:
        await insured_auth_service.reset_password(
            db, request.email, request.code, request.new_password, _client_ip(http)
        )
    except WeakPasswordError:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_CONTENT,
            detail=WEAK_PASSWORD,
        )
    except RateLimitedError as exc:
        raise _rate_limited(exc)
    except InvalidCredentialsError:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail=INVALID_CREDENTIALS)
    except SamePasswordError:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="La contraseña no es válida. Elija una diferente.",
        )
    return MessageResponse(message="Contraseña actualizada. Ingrese con su nueva contraseña.")


class RegistrationRefused(Exception):
    """Error del registro con un código estable para la app; el mensaje es genérico."""

    def __init__(self, status_code: int, code: str, detail: str):
        self.status_code, self.code, self.detail = status_code, code, detail

    def response(self) -> JSONResponse:
        return JSONResponse(status_code=self.status_code, content={"detail": self.detail, "code": self.code})


def registration_open() -> None:
    """Con REGISTRATION_ENABLED apagado el registro no existe: 404, como cualquier ruta desconocida (mejora FE-MOV-01)."""
    if not settings.registration_enabled:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Not Found")


def _unprocessable(code: str, detail: str) -> RegistrationRefused:
    return RegistrationRefused(status.HTTP_422_UNPROCESSABLE_CONTENT, code, detail)


async def _read_jpeg(upload: Optional[UploadFile], required: bool) -> Optional[bytes]:
    """Se lee con tope de 2 MB y se exige la firma JPEG, como las fotos del siniestro."""
    if upload is None:
        if required:
            raise _unprocessable("INVALID_PHOTO", "Faltan las fotos del documento o la selfie.")
        return None
    content = await upload.read(MAX_PHOTO_BYTES + 1)
    if len(content) > MAX_PHOTO_BYTES:
        raise RegistrationRefused(status.HTTP_413_CONTENT_TOO_LARGE, "INVALID_PHOTO", "Cada foto puede pesar máximo 2 MB.")
    if not content.startswith(JPEG_MAGIC):
        raise _unprocessable("INVALID_PHOTO", "Las fotos deben ser imágenes JPEG tomadas con la cámara.")
    return content


@router.post("/register/otp", response_model=OtpResponse, dependencies=[Depends(registration_open)])
async def request_registration_otp(
    request: RegistrationOtpRequest, http: Request, db: AsyncSession = Depends(get_db_session)
):
    """Código por SMS para comprobar el celular antes de crear la cuenta (mejora FE-MOV-01)."""
    try:
        code = await insured_registration_service.request_otp(
            db, request.document_type, request.document_number, request.phone, _client_ip(http)
        )
    except RateLimitedError as exc:
        raise _rate_limited(exc)
    except SmsUnavailableError:
        raise _sms_unavailable()
    response = _otp_response(code)
    response.message = REGISTRATION_CODE_SENT
    return response


async def _register(
    data: str,
    document_front: Optional[UploadFile],
    selfie: Optional[UploadFile],
    document_back: Optional[UploadFile],
    db: AsyncSession,
    storage: DocumentStorage,
    verifier: IdentityVerifier,
    ip: Optional[str],
) -> RegistrationOutcome:
    try:
        request = RegistrationData.model_validate_json(data)
    except ValidationError:
        raise _unprocessable("INVALID_DATA", "Los datos del registro no tienen el formato esperado.")
    photos = IdentityPhotos(
        document_front=await _read_jpeg(document_front, required=True),
        selfie=await _read_jpeg(selfie, required=True),
        document_back=await _read_jpeg(document_back, required=False),
    )
    new_insured = NewInsured(
        email=request.email, full_name=request.full_name, document_type=request.document_type,
        document_number=request.document_number, phone=request.phone, password=request.password,
        otp_code=request.otp_code,
    )
    try:
        return await insured_registration_service.register(db, storage, verifier, new_insured, photos, ip)
    except WeakPasswordError:
        raise _unprocessable("WEAK_PASSWORD", WEAK_PASSWORD)
    except RateLimitedError as exc:
        raise _rate_limited(exc)
    except InvalidRegistrationCodeError:
        raise _unprocessable("INVALID_CODE", "El código no es válido o ya venció. Pida uno nuevo.")
    except DuplicateAccountError:
        raise RegistrationRefused(status.HTTP_409_CONFLICT, "DUPLICATE", "No pudimos crear la cuenta con esos datos.")
    except IdentityRejectedError:
        raise _unprocessable("IDENTITY_REJECTED", "No pudimos verificar su identidad.")
    except StorageUnavailableError:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="No pudimos completar el registro. Intente en unos minutos.",
            headers={"Retry-After": "30"},
        )


@router.post(
    "/register",
    dependencies=[Depends(registration_open)],
    response_model=RegistrationResult,
    status_code=status.HTTP_201_CREATED,
    responses={202: {"model": RegistrationResult, "description": "Cuenta creada con la identidad en verificación"}},
)
async def register(
    response: Response,
    http: Request,
    data: str = Form(..., max_length=4_000),
    document_front: Optional[UploadFile] = File(None),
    selfie: Optional[UploadFile] = File(None),
    document_back: Optional[UploadFile] = File(None),
    db: AsyncSession = Depends(get_db_session),
    storage: DocumentStorage = Depends(get_document_storage),
    verifier: IdentityVerifier = Depends(get_identity_verifier),
):
    """Crea la cuenta con la foto del documento y la selfie (mejora FE-MOV-01).
    201 aprobada, 202 en verificación; las imágenes solo se guardan mientras está en verificación."""
    try:
        outcome = await _register(data, document_front, selfie, document_back, db, storage, verifier, _client_ip(http))
    except RegistrationRefused as refused:
        return refused.response()
    response.headers["Cache-Control"] = "no-store"
    if outcome.status != APPROVED:
        response.status_code = status.HTTP_202_ACCEPTED
    return RegistrationResult(**outcome.tokens.model_dump(), verification_status=outcome.status)

