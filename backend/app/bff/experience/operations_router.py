"""Back office de operaciones: siniestros, operaciones sobre la póliza, auditoría y reportes (HU-WEB-15).
Mismo acceso que la búsqueda: rol analyst y, si el usuario es de un socio, solo lo de su socio."""
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Any, Dict, List, Literal, Optional
from pydantic import BaseModel, Field

from fastapi import APIRouter, Depends, HTTPException, Path, Query, Response, status
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.bff.experience.router import operations_scope
from app.core.database import get_db_session
from app.core.security import require_roles
from app.core.storage import DocumentStorage, get_document_storage
from app.modules.claims.models import (
    ClaimAssignRequest,
    ClaimDecisionRequest,
    ClaimDetail,
    ClaimList,
    ClaimStatus,
    Expert,
)
from app.modules.claims.service import (
    ClaimClosedError,
    ClaimNotFoundError,
    InvalidSettlementError,
    claim_service,
)
from app.modules.policy.models import (
    CancellationRequest,
    EndorsementRequest,
    PolicyAudit,
    PolicyDB,
    PolicyOperationResult,
)
from app.modules.policy.operations import (
    InvalidEndorsementError,
    NotRenewableError,
    PolicyClosedError,
    policy_operations,
)
from app.modules.rating import partners
from app.modules.underwriting import audit
from app.modules.reports.service import build_report, to_csv, to_pdf

router = APIRouter(prefix="/experience", tags=["Operaciones"])

CLAIM_NUMBER = r"^SIN-\d{4}-\d{5}$"
POLICY_NUMBER = r"^POL-\d{4}-\d{5}$"
NOT_FOUND_CLAIM = "No encontramos el siniestro."
NOT_FOUND_POLICY = "No encontramos la póliza."


@dataclass
class Operator:
    username: str
    partner_id: Optional[str]


async def operator(
    claims: Dict[str, Any] = Depends(require_roles(["analyst"])),
    partner_id: Optional[str] = Depends(operations_scope),
) -> Operator:
    """Quién decide: queda como responsable en cada registro inmutable (AC-06)."""
    return Operator(username=claims["sub"], partner_id=partner_id)


def claim_errors(exc: Exception) -> HTTPException:
    if isinstance(exc, ClaimNotFoundError):
        return HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=NOT_FOUND_CLAIM)
    if isinstance(exc, ClaimClosedError):
        return HTTPException(status_code=status.HTTP_409_CONFLICT, detail="El siniestro ya tiene un dictamen registrado.")
    return HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(exc))


# -- Siniestros ------------------------------------------------------------------------------------


@router.get("/claims", response_model=ClaimList)
async def list_claims(
    claim_status: Optional[ClaimStatus] = Query(None, alias="status"),
    user: Operator = Depends(operator),
    db: AsyncSession = Depends(get_db_session),
):
    """Bandeja de siniestros, los más recientes primero (AC-01)."""
    return await claim_service.list_claims(db, claim_status, user.partner_id)


@router.get("/claims/{claim_number}", response_model=ClaimDetail)
async def get_claim(
    response: Response,
    claim_number: str = Path(..., pattern=CLAIM_NUMBER),
    user: Operator = Depends(operator),
    db: AsyncSession = Depends(get_db_session),
    storage: DocumentStorage = Depends(get_document_storage),
):
    """Expediente con fotos, ubicación, hora, huella e historial (AC-02, AC-03). Los enlaces de las fotos vencen."""
    response.headers["Cache-Control"] = "no-store"
    try:
        return await claim_service.detail(db, storage, claim_number, user.partner_id)
    except ClaimNotFoundError as exc:
        raise claim_errors(exc)


@router.post("/claims/{claim_number}/assign", status_code=status.HTTP_204_NO_CONTENT)
async def assign_claim(
    request: ClaimAssignRequest,
    claim_number: str = Path(..., pattern=CLAIM_NUMBER),
    user: Operator = Depends(operator),
    db: AsyncSession = Depends(get_db_session),
):
    """El analista toma el caso y puede asignar un perito (AC-07)."""
    try:
        await claim_service.assign(db, claim_number, request, user.username, user.partner_id)
    except (ClaimNotFoundError, ClaimClosedError, InvalidSettlementError) as exc:
        raise claim_errors(exc)


@router.post("/claims/{claim_number}/decision", status_code=status.HTTP_204_NO_CONTENT)
async def decide_claim(
    request: ClaimDecisionRequest,
    claim_number: str = Path(..., pattern=CLAIM_NUMBER),
    user: Operator = Depends(operator),
    db: AsyncSession = Depends(get_db_session),
):
    """Dictamen con justificación; si se liquida, valida el amparo y ordena el pago (AC-04, AC-05, AC-08)."""
    try:
        await claim_service.decide(db, claim_number, request, user.username, user.partner_id)
    except (ClaimNotFoundError, ClaimClosedError, InvalidSettlementError) as exc:
        raise claim_errors(exc)


@router.post("/claims/{claim_number}/payouts/retry", status_code=status.HTTP_204_NO_CONTENT)
async def retry_claim_payouts(
    claim_number: str = Path(..., pattern=CLAIM_NUMBER),
    user: Operator = Depends(operator),
    db: AsyncSession = Depends(get_db_session),
):
    """Reintenta los pagos rechazados del siniestro sin repetir los aprobados (AC-08)."""
    try:
        await claim_service.retry_payouts(db, claim_number, user.partner_id)
    except (ClaimNotFoundError, ClaimClosedError) as exc:
        raise claim_errors(exc)


@router.get("/experts", response_model=List[Expert])
async def list_experts(
    city: Optional[str] = Query(None, max_length=80),
    user: Operator = Depends(operator),
    db: AsyncSession = Depends(get_db_session),
):
    return await claim_service.experts(db, city)


# -- Operaciones sobre la póliza -------------------------------------------------------------------


async def scoped_policy(db: AsyncSession, policy_number: str, user: Operator) -> PolicyDB:
    stmt = select(PolicyDB).where(PolicyDB.policy_number == policy_number)
    if user.partner_id is not None:
        stmt = stmt.where(PolicyDB.partner_id == user.partner_id)
    policy = await db.scalar(stmt)
    if policy is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=NOT_FOUND_POLICY)
    return policy


@router.post("/policies/{policy_number}/endorsements", response_model=PolicyOperationResult)
async def endorse_policy(
    request: EndorsementRequest,
    policy_number: str = Path(..., pattern=POLICY_NUMBER),
    user: Operator = Depends(operator),
    db: AsyncSession = Depends(get_db_session),
):
    """Endoso de beneficiarios o de suma asegurada; la carátula siguiente sale con la versión nueva (AC-10)."""
    await scoped_policy(db, policy_number, user)
    try:
        return await policy_operations.endorse(db, policy_number, request, user.username)
    except PolicyClosedError:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="La póliza está cancelada o terminada.")
    except InvalidEndorsementError as exc:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(exc))


@router.post("/policies/{policy_number}/cancellation", response_model=PolicyOperationResult)
async def cancel_policy(
    request: CancellationRequest,
    policy_number: str = Path(..., pattern=POLICY_NUMBER),
    user: Operator = Depends(operator),
    db: AsyncSession = Depends(get_db_session),
):
    """Cancelación a pedido del cliente con motivo y devolución proporcional (AC-11)."""
    await scoped_policy(db, policy_number, user)
    try:
        return await policy_operations.cancel(db, policy_number, request, user.username)
    except PolicyClosedError:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="La póliza ya está cancelada o terminada.")


@router.post("/policies/{policy_number}/renewal", response_model=PolicyOperationResult)
async def renew_policy(
    policy_number: str = Path(..., pattern=POLICY_NUMBER),
    user: Operator = Depends(operator),
    db: AsyncSession = Depends(get_db_session),
):
    """Renovación por un año desde 30 días antes del vencimiento (HU-MOV-11, RC-24)."""
    await scoped_policy(db, policy_number, user)
    try:
        return await policy_operations.renew(db, policy_number, user.username)
    except partners.PartnerBlockedError as exc:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail=exc.detail)
    except NotRenewableError:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="La póliza se puede renovar desde 30 días antes de su vencimiento, si no está cancelada ni terminada.",
        )


# -- Socios B2B (FE-WEB-05): solo el equipo de Solventa ---------------------------------------------


class PartnerApprovalRequest(BaseModel):
    quota_limit: Optional[int] = Field(default=50000, ge=100)


class OperatorProfile(BaseModel):
    solventa: bool


# Acepta los ids con tildes de socios creados antes de que el alta los generara en ASCII.
PARTNER_ID = Path(..., min_length=2, max_length=64, pattern=r"^[\w-]+$")
NOT_FOUND_PARTNER = "Socio no encontrado."
REVOKED_PARTNER = "Socio revocado."


async def solventa_operator(user: Operator = Depends(operator)) -> Operator:
    if user.partner_id is not None:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Solo operadores centrales de Solventa pueden usar esta función.",
        )
    return user


def partner_errors(exc: Exception) -> HTTPException:
    if isinstance(exc, partners.PartnerNotFoundError):
        return HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=NOT_FOUND_PARTNER)
    return HTTPException(status_code=status.HTTP_409_CONFLICT, detail=REVOKED_PARTNER)


@router.get("/operator", response_model=OperatorProfile)
async def operator_profile(user: Operator = Depends(operator)):
    """Si el analista es de Solventa; el portal solo oculta sus pestañas («Socios», «Auditoría»), cada ruta lo vuelve a validar."""
    return OperatorProfile(solventa=user.partner_id is None)


@router.get("/partners", response_model=partners.PartnerPage)
async def list_partners(
    response: Response,
    user: Operator = Depends(solventa_operator),
    db: AsyncSession = Depends(get_db_session),
):
    """Socios con su entorno, estado y cuota; sin secretos ni el correo de contacto completo. `truncated` avisa si hay más."""
    response.headers["Cache-Control"] = "no-store"
    return await partners.list_partners(db)


@router.post("/partners/{partner_id}/revoke", response_model=partners.PartnerSummary)
async def revoke_partner(
    partner_id: str = PARTNER_ID,
    user: Operator = Depends(solventa_operator),
    db: AsyncSession = Depends(get_db_session),
):
    """Revoca al socio; sus tokens vigentes dejan de servir en el siguiente pedido."""
    try:
        return await partners.revoke(db, partner_id, user.username)
    except partners.PartnerNotFoundError as exc:
        raise partner_errors(exc)


@router.post("/partners/{partner_id}/credentials", response_model=partners.PartnerCredentials)
async def rotate_partner_credentials(
    response: Response,
    partner_id: str = PARTNER_ID,
    user: Operator = Depends(solventa_operator),
    db: AsyncSession = Depends(get_db_session),
):
    """Rota el secreto del socio y lo entrega una sola vez."""
    response.headers["Cache-Control"] = "no-store"
    try:
        return await partners.rotate_secret(db, partner_id, user.username)
    except (partners.PartnerNotFoundError, partners.PartnerRevokedError) as exc:
        raise partner_errors(exc)


@router.post("/partners/{partner_id}/approve")
async def approve_partner_production(
    partner_id: str = PARTNER_ID,
    request: Optional[PartnerApprovalRequest] = None,
    user: Operator = Depends(solventa_operator),
    db: AsyncSession = Depends(get_db_session),
):
    """Aprueba un socio en sandbox y lo promueve a producción con cuota ampliada (RC-23, AC-15, SR-09)."""
    quota = request.quota_limit if request and request.quota_limit else 50000
    try:
        partner = await partners.approve(db, partner_id, quota, user.username)
    except (partners.PartnerNotFoundError, partners.PartnerRevokedError) as exc:
        raise partner_errors(exc)
    return {
        "status": "APPROVED",
        "partner_id": partner.partner_id,
        "environment": partner.environment,
        "quota_limit": partner.quota_limit,
        "message": f"Socio {partner.name} promovido a producción con cuota de {partner.quota_limit} cotizaciones/hora.",
    }


# -- Auditoría de suscripción (FE-WEB-08): solo el equipo de Solventa --------------------------------

DECISION_ID = Path(..., pattern=r"^(QUO|UWR)-[0-9A-F]{12}$")
# También en los errores: HTTPException no lleva las cabeceras puestas en `response`.
NO_STORE = {"Cache-Control": "no-store"}


class ConsentRevocationRequest(BaseModel):
    # En el cuerpo y no en la ruta: así el documento no queda en los registros de acceso.
    document_number: str = Field(..., pattern=r"^[A-Za-z0-9]{4,20}$")


@router.get("/decisions/{decision_id}", response_model=audit.DecisionDetail)
async def decision_detail(
    response: Response,
    decision_id: str = DECISION_ID,
    user: Operator = Depends(solventa_operator),
    db: AsyncSession = Depends(get_db_session),
):
    """Entradas, versión de reglas y salida de una cotización o un rechazo; el documento va enmascarado."""
    response.headers["Cache-Control"] = "no-store"
    try:
        return await audit.detail(db, decision_id, user.username)
    except audit.DecisionNotFoundError:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="No encontramos la decisión.", headers=NO_STORE)


@router.post("/decisions/{decision_id}/replay", response_model=audit.ReplayResult)
async def replay_decision(
    response: Response,
    decision_id: str = DECISION_ID,
    user: Operator = Depends(solventa_operator),
    db: AsyncSession = Depends(get_db_session),
):
    """Recalcula una cotización con las reglas vigentes y dice si coincide; un rechazo o una decisión con el
    consentimiento revocado no se recalculan (409)."""
    response.headers["Cache-Control"] = "no-store"
    try:
        return await audit.replay(db, decision_id, user.username)
    except audit.DecisionNotFoundError:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="No encontramos la decisión.", headers=NO_STORE)
    except audit.RejectionNotReplayableError:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail=audit.REJECTION_NOT_REPLAYABLE, headers=NO_STORE)
    except audit.ConsentRevokedNotReplayableError:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail=audit.CONSENT_REVOKED_NOT_REPLAYABLE, headers=NO_STORE)
    except audit.DecisionNotReplayableError:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="Decisión anterior a la auditoría: no se puede recalcular.",
            headers=NO_STORE,
        )


@router.post("/consents/revoke", response_model=audit.ConsentRevocation)
async def revoke_consent(
    request: ConsentRevocationRequest,
    response: Response,
    user: Operator = Depends(solventa_operator),
    db: AsyncSession = Depends(get_db_session),
):
    """Registra la revocación del consentimiento del titular; ya no sirve para cotizar."""
    response.headers["Cache-Control"] = "no-store"
    return await audit.revoke_consent(db, request.document_number, user.username)


@router.get("/policies/{policy_number}/audit", response_model=PolicyAudit)
async def audit_policy(
    policy_number: str = Path(..., pattern=POLICY_NUMBER),
    user: Operator = Depends(operator),
    db: AsyncSession = Depends(get_db_session),
):
    """Decisiones sobre la póliza en orden y de solo lectura (AC-14)."""
    return await policy_operations.audit(db, await scoped_policy(db, policy_number, user))


# -- Reportes --------------------------------------------------------------------------------------


@router.get("/reports/{period}")
async def monthly_report(
    period: str = Path(..., pattern=r"^\d{4}-(0[1-9]|1[0-2])$"),
    report_format: Literal["csv", "pdf"] = Query("csv", alias="format"),
    user: Operator = Depends(operator),
    db: AsyncSession = Depends(get_db_session),
):
    """Reporte al regulador y al reasegurador de todo Solventa: un usuario de un socio no lo descarga (AC-13)."""
    if user.partner_id is not None:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Los reportes son solo para Solventa.")
    report = await build_report(db, period)
    if report_format == "pdf":
        body, media = to_pdf(report, datetime.now(timezone.utc)), "application/pdf"
    else:
        body, media = to_csv(report), "text/csv; charset=utf-8"
    return Response(
        content=body,
        media_type=media,
        headers={
            "Content-Disposition": f'attachment; filename="reporte-{period}.{report_format}"',
            "Cache-Control": "no-store",
        },
    )
