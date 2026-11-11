"""Router BFF del asegurado en la app móvil: su billetera de pólizas (HU-MOV-04)."""
from typing import Any, Dict

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db_session
from app.core.security import require_roles
from app.modules.identity.service import InvalidTokenError, insured_auth_service
from app.modules.policy.models import InsuredPolicies, PolicyDB, PolicyOperationResult
from app.modules.policy.operations import PolicyClosedError, policy_operations
from app.modules.policy.service import policy_service
from sqlalchemy import select

router = APIRouter(prefix="/insured", tags=["Asegurado"])

require_insured = require_roles(["insured"])


@router.get("/policies", response_model=InsuredPolicies)
async def my_policies(claims: Dict[str, Any] = Depends(require_insured), db: AsyncSession = Depends(get_db_session)):
    """Pólizas vigentes del titular del token; no recibe identificadores del cliente."""
    try:
        account = await insured_auth_service.account_for_access(db, claims)
    except InvalidTokenError:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Sesión revocada o vencida.",
            headers={"WWW-Authenticate": "Bearer"},
        )
    policies = await policy_service.active_for_holder(db, account.document_number, account.full_name)
    return InsuredPolicies(policies=policies)


@router.post("/policies/{policy_number}/renewal", response_model=PolicyOperationResult)
async def renew_my_policy(
    policy_number: str,
    claims: Dict[str, Any] = Depends(require_insured),
    db: AsyncSession = Depends(get_db_session),
):
    """Renovación de póliza por el asegurado titular (HU-MOV-11 / RC-27 / RC-24)."""
    try:
        account = await insured_auth_service.account_for_access(db, claims)
    except InvalidTokenError:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Sesión revocada o vencida.",
            headers={"WWW-Authenticate": "Bearer"},
        )

    policy = (await db.execute(select(PolicyDB).where(PolicyDB.policy_number == policy_number))).scalar_one_or_none()
    if not policy or policy.insured_document != account.document_number:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Póliza no encontrada.")

    try:
        return await policy_operations.renew(db, policy_number, actor=f"Asegurado {account.document_number}")
    except (PolicyClosedError, ValueError) as exc:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(exc))
