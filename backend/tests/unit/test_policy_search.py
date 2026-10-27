"""Enmascarado del documento y texto literal en la búsqueda (HU-WEB-08: TC-05)."""
import pytest

from app.modules.policy.search import escape_like, mask_document

pytestmark = pytest.mark.unit


@pytest.mark.parametrize(
    "document, masked",
    [("1020304050", "******4050"), ("12345", "*2345"), ("1234", "****"), ("", "")],
)
def test_tc_05_el_documento_solo_deja_ver_los_ultimos_cuatro_digitos(document, masked):
    assert mask_document(document) == masked


def test_tc_05_los_comodines_del_texto_se_buscan_literales():
    assert escape_like("50%_a\\b") == "50\\%\\_a\\\\b"
