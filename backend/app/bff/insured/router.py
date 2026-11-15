"""Router BFF del asegurado en la app móvil: billetera (HU-MOV-04), siniestros, carátula, renovación y avisos (HU-MOV-11),
y envío del reporte del siniestro (HU-MOV-09)."""
from typing import Any, Dict, Optional

import jwt
from fastapi import APIRouter, Depends, File, Form, HTTPException, Path, Response, UploadFile, status
from fastapi.security import HTTPAuthorizationCredentials
from pydantic import ValidationError
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db_session
from app.core.security import decode_access_token, require_roles, security_bearer
from app.core.storage import DocumentStorage, StorageUnavailableError, get_document_storage
from app.modules.claims.insured import InsuredClaims, claims_of
from app.modules.claims.intake import (
    MAX_PHOTO_BYTES,
    MAX_PHOTOS,
    ClaimReceipt,
    ClaimReportRequest,
    PhotoRejectedError,
    PhotoTooLargeError,
    PolicyNotAvailableError,
    claim_intake,
)
from app.modules.identity.db_models import InsuredAccountDB
from app.modules.identity.service import InvalidTokenError, insured_auth_service
from app.modules.notifications.service import (
    PushPreferences, PushPreferencesRequest, PushTokenRequest, notification_service,
)
from app.modules.policy import document as policy_document
from app.modules.policy.models import CaratulaLink, InsuredPolicies, PolicyDB, PolicyOperationResult
from app.modules.policy.operations import NotRenewableError, policy_operations
from app.modules.policy.service import policy_service

router = APIRouter(prefix="/insured", tags=["Asegurado"])

require_insured = require_roles(["insured"])
POLICY_NUMBER = r"^POL-\d{4}-\d{5}$"
NOT_FOUND = "No encontramos la póliza."


async def insured_account(
    claims: Dict[str, Any] = Depends(require_insured), db: AsyncSession = Depends(get_db_session)
) -> InsuredAccountDB:
    """La cuenta del token; el documento nunca llega del cliente."""
    try:
        return await insured_auth_service.account_for_access(db, claims)
    except InvalidTokenError:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Sesión revocada o vencida.",
            headers={"WWW-Authenticate": "Bearer"},
        )


async def own_policy(db: AsyncSession, policy_number: str, account: InsuredAccountDB) -> PolicyDB:
    """Una póliza de otra persona responde igual que una inexistente (HU-MOV-11, AC-05)."""
    policy = await db.scalar(select(PolicyDB).where(
        PolicyDB.policy_number == policy_number, PolicyDB.insured_document == account.document_number
    ))
    if policy is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=NOT_FOUND)
    return policy


@router.get("/policies", response_model=InsuredPolicies)
async def my_policies(account: InsuredAccountDB = Depends(insured_account), db: AsyncSession = Depends(get_db_session)):
    """Pólizas vigentes del titular del token; no recibe identificadores del cliente."""
    policies = await policy_service.active_for_holder(db, account.document_number, account.full_name)
    return InsuredPolicies(policies=policies)


@router.get("/claims", response_model=InsuredClaims)
async def my_claims(
    response: Response, account: InsuredAccountDB = Depends(insured_account), db: AsyncSession = Depends(get_db_session)
):
    """Siniestros del titular con su línea de tiempo en lenguaje claro (HU-MOV-11)."""
    response.headers["Cache-Control"] = "no-store"
    return await claims_of(db, account.document_number)


@router.get("/policies/{policy_number}/caratula", response_model=CaratulaLink)
async def my_caratula(
    response: Response,
    policy_number: str = Path(..., pattern=POLICY_NUMBER),
    account: InsuredAccountDB = Depends(insured_account),
    db: AsyncSession = Depends(get_db_session),
    storage: DocumentStorage = Depends(get_document_storage),
):
    """Enlace de 15 minutos a la carátula de una póliza propia (HU-MOV-11, RC-40)."""
    response.headers["Cache-Control"] = "no-store"
    await own_policy(db, policy_number, account)
    try:
        link = await policy_document.get_caratula_link(db, storage, policy_number, None)
    except policy_document.PolicyCancelledError:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="La póliza está cancelada y no tiene carátula vigente.")
    except StorageUnavailableError:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="La carátula está en generación. Intente de nuevo en unos segundos.",
            headers={"Retry-After": "5"},
        )
    return link


@router.post("/policies/{policy_number}/renewal", response_model=PolicyOperationResult)
async def renew_my_policy(
    policy_number: str = Path(..., pattern=POLICY_NUMBER),
    account: InsuredAccountDB = Depends(insured_account),
    db: AsyncSession = Depends(get_db_session),
):
    """Renovación por un año, desde 30 días antes del vencimiento (HU-MOV-11, RC-24 y RC-27)."""
    await own_policy(db, policy_number, account)
    try:
        return await policy_operations.renew(db, policy_number, "Asegurado desde la app")
    except NotRenewableError:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="La póliza se puede renovar desde 30 días antes de su vencimiento.",
        )


@router.get("/devices/push-preferences", response_model=PushPreferences)
async def my_push_preferences(
    account: InsuredAccountDB = Depends(insured_account), db: AsyncSession = Depends(get_db_session)
):
    """Categorías de avisos que acepta el asegurado; sin preferencia guardada, todas (mejora FE-MOV-04)."""
    return await notification_service.preferences(db, account.document_number)


@router.put("/devices/push-preferences", response_model=PushPreferences)
async def save_push_preferences(
    request: PushPreferencesRequest,
    account: InsuredAccountDB = Depends(insured_account),
    db: AsyncSession = Depends(get_db_session),
):
    """Guarda las tres categorías de avisos del asegurado del token (mejora FE-MOV-04)."""
    return await notification_service.save_preferences(db, account.document_number, request)


@router.post("/devices/push-token", status_code=status.HTTP_204_NO_CONTENT)
async def register_push_token(
    request: PushTokenRequest,
    account: InsuredAccountDB = Depends(insured_account),
    db: AsyncSession = Depends(get_db_session),
):
    """Registra el teléfono para recibir avisos de siniestros y vencimientos (HU-MOV-11, RC-15)."""
    await notification_service.register(db, account.document_number, request.token)


UNAUTHORIZED = HTTPException(
    status_code=status.HTTP_401_UNAUTHORIZED,
    detail="Sesión revocada o vencida.",
    headers={"WWW-Authenticate": "Bearer"},
)


async def reporting_account(
    credentials: Optional[HTTPAuthorizationCredentials] = Depends(security_bearer),
    db: AsyncSession = Depends(get_db_session),
) -> InsuredAccountDB:
    """El token de acceso de la sesión o, con la app cerrada, el token que solo sirve para reportar (SR-02)."""
    if credentials is None:
        raise UNAUTHORIZED
    try:
        claims = decode_access_token(credentials.credentials)
    except jwt.PyJWTError:
        raise UNAUTHORIZED
    if claims.get("role") != "insured":
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Permisos insuficientes para acceder a este recurso")
    try:
        if claims.get("type") == "access":
            return await insured_auth_service.account_for_access(db, claims)
        return await insured_auth_service.account_for_report(db, claims)
    except InvalidTokenError:
        raise UNAUTHORIZED


async def read_photo(upload: Optional[UploadFile]) -> Optional[bytes]:
    if upload is None:
        return None
    content = await upload.read(MAX_PHOTO_BYTES + 1)
    if len(content) > MAX_PHOTO_BYTES:
        raise PhotoTooLargeError()
    return content


@router.post("/claims", response_model=ClaimReceipt, status_code=status.HTTP_201_CREATED)
async def report_claim(
    response: Response,
    report: str = Form(..., max_length=20_000),
    photo_1: Optional[UploadFile] = File(None),
    photo_2: Optional[UploadFile] = File(None),
    photo_3: Optional[UploadFile] = File(None),
    photo_4: Optional[UploadFile] = File(None),
    photo_5: Optional[UploadFile] = File(None),
    account: InsuredAccountDB = Depends(reporting_account),
    db: AsyncSession = Depends(get_db_session),
    storage: DocumentStorage = Depends(get_document_storage),
):
    """Registra el reporte una sola vez, con las fotos verificadas contra su huella (HU-MOV-09, AC-03 y AC-06)."""
    try:
        request = ClaimReportRequest.model_validate_json(report)
    except ValidationError:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_CONTENT, detail="El reporte no tiene el formato esperado.")
    try:
        uploads = [photo_1, photo_2, photo_3, photo_4, photo_5][:MAX_PHOTOS]
        files = {position: content for position, upload in enumerate(uploads, start=1)
                 if (content := await read_photo(upload)) is not None}
        result, created = await claim_intake.receive(db, storage, account.document_number, request, files)
    except PolicyNotAvailableError:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="No encontramos una póliza vigente para este reporte.")
    except PhotoTooLargeError:
        raise HTTPException(status_code=status.HTTP_413_CONTENT_TOO_LARGE, detail="Cada foto puede pesar máximo 2 MB.")
    except PhotoRejectedError as exc:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_CONTENT, detail=exc.message)
    except StorageUnavailableError:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="No pudimos guardar las fotos. El envío se reintentará solo.",
            headers={"Retry-After": "30"},
        )
    if not created:
        response.status_code = status.HTTP_200_OK
    return result
