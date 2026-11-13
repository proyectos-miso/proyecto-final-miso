"""Router BFF del asegurado en la app móvil: billetera (HU-MOV-04), siniestros, carátula, renovación y avisos (HU-MOV-11)."""
from typing import Any, Dict

from fastapi import APIRouter, Depends, HTTPException, Path, Response, status
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db_session
from app.core.security import require_roles
from app.core.storage import DocumentStorage, StorageUnavailableError, get_document_storage
from app.modules.claims.insured import InsuredClaims, claims_of
from app.modules.identity.db_models import InsuredAccountDB
from app.modules.identity.service import InvalidTokenError, insured_auth_service
from app.modules.notifications.service import PushTokenRequest, notification_service
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


@router.post("/devices/push-token", status_code=status.HTTP_204_NO_CONTENT)
async def register_push_token(
    request: PushTokenRequest,
    account: InsuredAccountDB = Depends(insured_account),
    db: AsyncSession = Depends(get_db_session),
):
    """Registra el teléfono para recibir avisos de siniestros y vencimientos (HU-MOV-11, RC-15)."""
    await notification_service.register(db, account.document_number, request.token)
