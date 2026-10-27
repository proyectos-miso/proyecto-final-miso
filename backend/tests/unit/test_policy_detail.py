"""Enmascarado del contacto del titular en el detalle (HU-WEB-09: TC-02)."""
import pytest

from app.modules.policy.detail import mask_email, mask_phone


@pytest.mark.parametrize(
    "email, masked",
    [
        ("carolina.prueba@example.com", "c*****@example.com"),
        ("a@example.com", "a*****@example.com"),
        ("titular@correo.example", "t*****@correo.example"),
    ],
)
def test_tc_02_el_correo_deja_ver_la_primera_letra_y_el_dominio(email, masked):
    assert mask_email(email) == masked


@pytest.mark.parametrize(
    "phone, masked",
    [
        ("3001112233", "******2233"),
        ("+57 300 111 2233", "********2233"),
        ("1234", "****"),
    ],
)
def test_tc_02_el_telefono_deja_ver_los_ultimos_cuatro_digitos(phone, masked):
    assert mask_phone(phone) == masked
