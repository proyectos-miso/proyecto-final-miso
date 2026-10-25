"""Reglas de cupo de aprobación directa (HU-WEB-07: TC-01)."""
import pytest

from app.modules.rating.service import NEUTRAL_SCORE
from app.modules.underwriting.rules import REASONS, evaluate

pytestmark = pytest.mark.unit


@pytest.mark.parametrize(
    "score, limit",
    [(820, 1_200_000_000), (800, 1_200_000_000), (700, 700_000_000), (650, 700_000_000), (520, 350_000_000), (300, 150_000_000)],
)
def test_tc_01_el_cupo_se_aprueba_y_un_peso_mas_requiere_revision(score, limit):
    assert evaluate(limit, 40, score) is None

    decision = evaluate(limit + 1, 40, score)

    assert decision.rule_id == "UW-01"
    assert decision.max_approvable_amount == limit
    assert decision.reason == REASONS["UW-01"]
    assert decision.decision == "REVIEW_REQUIRED"


def test_tc_01_a_los_70_anos_manda_el_cupo_por_edad_si_es_menor():
    decision = evaluate(500_000_000, 70, 820)

    assert decision.rule_id == "UW-02"
    assert decision.max_approvable_amount == 300_000_000


def test_tc_01_a_los_65_anos_no_aplica_el_cupo_por_edad():
    assert evaluate(500_000_000, 65, 820) is None


def test_tc_01_con_perfil_bajo_y_edad_alta_manda_el_cupo_menor():
    decision = evaluate(200_000_000, 70, 300)

    assert decision.rule_id == "UW-01"
    assert decision.max_approvable_amount == 150_000_000


def test_tc_01_el_puntaje_neutro_usa_el_cupo_de_su_franja():
    assert evaluate(700_000_000, 40, NEUTRAL_SCORE) is None
    assert evaluate(700_000_001, 40, NEUTRAL_SCORE).max_approvable_amount == 700_000_000


def test_el_motivo_no_expone_codigos_ni_puntajes():
    for reason in REASONS.values():
        assert "UW-" not in reason and "puntaje" not in reason.lower()
