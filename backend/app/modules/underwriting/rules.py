"""Reglas de suscripción: cupo de aprobación directa por perfil crediticio y por edad (HU-WEB-07, HA-20).

Los cupos son una propuesta pendiente de validar con el negocio (DEC-01); este es el único lugar donde cambian.
"""
from typing import List, Optional, Tuple

from app.modules.underwriting.models import UnderwritingDecision

# Cupo en COP por puntaje del buró, con las mismas franjas que la tarifa.
SCORE_LIMITS: List[Tuple[int, float]] = [(800, 1_200_000_000), (650, 700_000_000), (500, 350_000_000), (0, 150_000_000)]
# Cupo en COP por edad; por debajo de 66 años solo aplica el del perfil crediticio.
AGE_LIMITS: List[Tuple[int, float]] = [(66, 300_000_000)]

REASONS = {
    "UW-01": "La suma asegurada solicitada supera el cupo de aprobación directa para el perfil crediticio del cliente.",
    "UW-02": "La suma asegurada solicitada supera el cupo de aprobación directa para clientes mayores de 65 años.",
}


def score_limit(score: float) -> float:
    return next(limit for floor, limit in SCORE_LIMITS if score >= floor)


def age_limit(age: int) -> Optional[float]:
    return next((limit for from_age, limit in AGE_LIMITS if age >= from_age), None)


def evaluate(insured_amount: float, age: int, score: float) -> Optional[UnderwritingDecision]:
    """Rechazo con la regla que fija el cupo menor, o None si la suma está dentro del cupo (el cupo se aprueba)."""
    candidates = [("UW-01", score_limit(score))]
    by_age = age_limit(age)
    if by_age is not None:
        candidates.append(("UW-02", by_age))
    rule_id, limit = min(candidates, key=lambda candidate: candidate[1])
    if insured_amount <= limit:
        return None
    return UnderwritingDecision(rule_id=rule_id, reason=REASONS[rule_id], max_approvable_amount=limit)
