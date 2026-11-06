"""Reglas de los siniestros paramétricos: un evento externo que supera su umbral se liquida sin analista (RC-28).
Las reglas viven solo aquí, como las de suscripción (HA-20)."""
from dataclasses import dataclass
from typing import Optional


@dataclass(frozen=True)
class ParametricRule:
    code: str
    measure: str
    threshold: float
    coverage_id: str
    payout_ratio: float
    description: str


RULES = {
    "SISMO": ParametricRule(
        code="SISMO-6.5",
        measure="magnitude",
        threshold=6.5,
        coverage_id="disability",
        payout_ratio=0.05,
        description="Sismo de magnitud 6,5 o más: auxilio del 5 % de la cobertura de incapacidad",
    ),
}


def matching_rule(event: Optional[dict]) -> Optional[ParametricRule]:
    """La regla del tipo de evento, si su medida alcanza el umbral; si no, el siniestro va a un analista."""
    if not event:
        return None
    rule = RULES.get(str(event.get("type", "")).upper())
    if rule is None:
        return None
    try:
        value = float(event.get(rule.measure))
    except (TypeError, ValueError):
        return None
    return rule if value >= rule.threshold else None
