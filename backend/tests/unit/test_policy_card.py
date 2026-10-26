"""Tarjeta de la billetera a partir de la póliza emitida (HU-MOV-04: TC-11)."""
from datetime import datetime, timezone

import pytest

from app.modules.policy.models import PolicyDB
from app.modules.policy.service import PolicyService

pytestmark = pytest.mark.unit


def policy(coverages: list) -> PolicyDB:
    return PolicyDB(
        policy_number="POL-2026-00001",
        conditions={"tier": "standard", "coverages": coverages},
        issued_at=datetime(2026, 1, 1, tzinfo=timezone.utc),
        valid_until=datetime(2027, 1, 1, tzinfo=timezone.utc),
    )


def test_tc_11_la_suma_asegurada_es_el_limite_de_la_cobertura_de_muerte():
    card = PolicyService.card(
        policy(
            [
                {"id": "critical_illness", "name": "Enfermedades graves", "limit": 3e8},
                {"id": "death", "name": "Muerte por cualquier causa", "limit": 2.5e8},
            ]
        ),
        "Asegurado de Prueba",
    )

    assert card.insured_amount == 2.5e8


def test_tc_11_sin_cobertura_de_muerte_usa_el_mayor_limite_incluido():
    card = PolicyService.card(
        policy(
            [
                {"id": "a", "name": "A", "limit": 1e8},
                {"id": "b", "name": "B", "limit": 9e8, "included": False},
                {"id": "c", "name": "C", "limit": 2e8},
            ]
        ),
        "Asegurado de Prueba",
    )

    assert card.insured_amount == 2e8
    assert [c.name for c in card.coverages] == ["A", "C"]
