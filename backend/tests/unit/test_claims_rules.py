"""Reglas de HU-WEB-15: reparto del pago, devolución proporcional, endoso de suma, regla paramétrica y beneficiarios."""
from datetime import datetime, timezone
from types import SimpleNamespace

import pytest
from pydantic import ValidationError

from app.modules.claims.parametric import matching_rule
from app.modules.payments.payouts import Payee, split
from app.modules.policy.models import Beneficiary, PolicyIssueRequest
from app.modules.policy.operations import prorated_refund, scaled_coverages

COVERAGES = {"coverages": [
    {"id": "death", "name": "Muerte", "limit": 200_000_000, "included": True},
    {"id": "disability", "name": "Incapacidad", "limit": 200_000_000, "included": True},
    {"id": "critical_illness", "name": "Enfermedades graves", "limit": 100_000_000, "included": True},
]}


def test_el_pago_se_reparte_por_porcentaje_y_el_ultimo_recibe_el_redondeo():
    payees = [Payee("A", "1", 33.33), Payee("B", "2", 33.33), Payee("C", "3", 33.34)]

    shares = split(1_000_001, payees)

    assert sum(shares) == 1_000_001
    assert shares[:2] == [333_300, 333_300]


def test_la_devolucion_es_la_prima_de_los_dias_que_faltan_del_mes_pagado():
    policy = SimpleNamespace(status="ACTIVE", issued_at=datetime(2026, 10, 1, tzinfo=timezone.utc),
                             conditions={"monthly_premium": 300_000})

    half = prorated_refund(policy, datetime(2026, 10, 16, 12, tzinfo=timezone.utc))
    next_month = prorated_refund(policy, datetime(2026, 11, 1, tzinfo=timezone.utc))
    unpaid = prorated_refund(SimpleNamespace(**{**policy.__dict__, "status": "ISSUED"}),
                             datetime(2026, 10, 16, tzinfo=timezone.utc))

    assert 140_000 < half < 160_000
    assert next_month == 300_000
    assert unpaid == 0


def test_el_endoso_de_suma_baja_todas_las_coberturas_en_la_misma_proporcion():
    limits = [c["limit"] for c in scaled_coverages(COVERAGES, 100_000_000)]

    assert limits == [100_000_000, 100_000_000, 50_000_000]


@pytest.mark.parametrize(
    "event, code",
    [
        ({"type": "SISMO", "magnitude": 6.5}, "SISMO-6.5"),
        ({"type": "sismo", "magnitude": "7"}, "SISMO-6.5"),
        ({"type": "SISMO", "magnitude": 6.4}, None),
        ({"type": "SISMO"}, None),
        ({"type": "SISMO", "magnitude": "alto"}, None),
        ({"type": "INUNDACION", "level": 9}, None),
        (None, None),
    ],
)
def test_la_regla_parametrica_solo_aplica_desde_el_umbral(event, code):
    rule = matching_rule(event)

    assert (rule.code if rule else None) == code


def beneficiary(**fields):
    base = {"name": "Lucía Prueba", "document_type": "CC", "document_number": "52000111", "relationship": "CONYUGE",
            "percentage": 100}
    return {**base, **fields}


def test_los_beneficiarios_deben_sumar_100_y_no_repetirse():
    ok = PolicyIssueRequest(quote_id="Q", tier="standard", beneficiaries=[beneficiary()])

    assert isinstance(ok.beneficiaries[0], Beneficiary)
    for invalid in ([beneficiary(percentage=99)], [beneficiary(percentage=50), beneficiary(percentage=50)],
                    [beneficiary(relationship="VECINO")], []):
        with pytest.raises(ValidationError):
            PolicyIssueRequest(quote_id="Q", tier="standard", beneficiaries=invalid)
