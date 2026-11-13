"""Siniestros del asegurado con su línea de tiempo en lenguaje claro (HU-MOV-11).
Lee solo el resumen y el historial (HA-04, RC-16): ni fotos, ni perito, ni montos internos del dictamen."""
from datetime import datetime, timezone
from typing import List, Optional

from pydantic import BaseModel
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.modules.claims.models import ClaimDB, ClaimEventDB
from app.modules.policy.models import PolicyDB

STATUS_LABELS = {
    "REPORTED": "Recibido",
    "IN_REVIEW": "En evaluación",
    "REJECTED": "Rechazado",
    "SETTLED": "Aprobado",
    "PAID": "Pagado",
}
STEPS = {
    "REPORTED": ("RECEIVED", "Recibido", "Recibimos su reporte con el radicado y las fotos. Pronto lo revisará un analista."),
    "ASSIGNED": ("IN_REVIEW", "En evaluación", "Un analista de Solventa está revisando su caso y la evidencia que envió."),
    "EXPERT_ASSIGNED": ("IN_REVIEW", "En evaluación",
                        "Asignamos un perito para revisar el caso en el lugar. Lo contactará para coordinar la visita."),
    "SETTLED": ("APPROVED", "Aprobado", "Su reclamación fue aprobada. Estamos haciendo el pago a los beneficiarios."),
    "REJECTED": ("REJECTED", "Rechazado", "Su reclamación no fue aprobada. Abajo le explicamos el motivo."),
    "PAID": ("PAID", "Pagado", "El pago se hizo a los beneficiarios de la póliza."),
}
PARAMETRIC_REPORTED = "Un evento externo cumplió la condición de su póliza y abrimos el siniestro automáticamente."
REVIEW_CHANNEL = (
    "Si no está de acuerdo, puede pedir una revisión dentro de los 30 días siguientes escribiendo a "
    "revisiones@solventa.example o con su asesor, citando el radicado."
)


class TimelineStep(BaseModel):
    step: str
    label: str
    explanation: str
    at: datetime


class InsuredClaim(BaseModel):
    claim_number: str
    policy_number: str
    reported_at: datetime
    status: str
    status_label: str
    last_change_at: datetime
    timeline: List[TimelineStep]
    rejection_reason: Optional[str] = None
    review_channel: Optional[str] = None


class InsuredClaims(BaseModel):
    updated_at: datetime
    claims: List[InsuredClaim]


def timeline(claim: ClaimDB, events: List[ClaimEventDB]) -> List[TimelineStep]:
    """Un paso por etapa, en orden; dos avisos de evaluación seguidos cuentan como uno."""
    steps: List[TimelineStep] = []
    for event in events:
        if event.kind not in STEPS:
            continue
        step, label, explanation = STEPS[event.kind]
        if event.kind == "REPORTED" and claim.origin == "PARAMETRIC":
            explanation = PARAMETRIC_REPORTED
        if steps and steps[-1].step == step and event.kind != "EXPERT_ASSIGNED":
            continue
        steps.append(TimelineStep(step=step, label=label, explanation=explanation, at=event.created_at))
    return steps


async def claims_of(session: AsyncSession, document_number: str) -> InsuredClaims:
    """Solo los siniestros de pólizas cuyo asegurado es el titular de la cuenta del token (AC-05)."""
    claims = list(await session.scalars(
        select(ClaimDB)
        .join(PolicyDB, PolicyDB.policy_number == ClaimDB.policy_number)
        .where(PolicyDB.insured_document == document_number)
        .order_by(ClaimDB.reported_at.desc(), ClaimDB.claim_number.desc())
    ))
    numbers = [c.claim_number for c in claims]
    events = list(await session.scalars(
        select(ClaimEventDB).where(ClaimEventDB.claim_number.in_(numbers)).order_by(ClaimEventDB.id)
    )) if numbers else []
    result = []
    for claim in claims:
        own = [e for e in events if e.claim_number == claim.claim_number]
        steps = timeline(claim, own)
        rejection = next((e.justification for e in reversed(own) if e.kind == "REJECTED"), None)
        result.append(InsuredClaim(
            claim_number=claim.claim_number,
            policy_number=claim.policy_number,
            reported_at=claim.reported_at,
            status=claim.status,
            status_label=STATUS_LABELS.get(claim.status, claim.status),
            last_change_at=steps[-1].at if steps else claim.reported_at,
            timeline=steps,
            rejection_reason=rejection,
            review_channel=REVIEW_CHANNEL if claim.status == "REJECTED" else None,
        ))
    return InsuredClaims(updated_at=datetime.now(timezone.utc), claims=result)
