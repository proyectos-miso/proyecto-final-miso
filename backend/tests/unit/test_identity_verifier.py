"""Proveedor de identidad simulado y límite de intentos del registro (mejora FE-MOV-01)."""
import asyncio
from datetime import datetime, timedelta, timezone

import pytest

from app.modules.identity import policy
from app.modules.identity.adapters import SimulatedIdentityVerifier
from app.modules.identity.ports import APPROVED, PENDING, REJECTED, VerificationResult
from app.modules.identity.service_registration import InsuredRegistrationService

pytestmark = pytest.mark.unit

JPEG = b"\xff\xd8\xff\xe0prueba"
NOW = datetime(2026, 11, 15, 12, 0, tzinfo=timezone.utc)


async def verify(verifier: SimulatedIdentityVerifier, document: str) -> VerificationResult:
    return await verifier.verify("CC", document, "Ana Prueba", JPEG, JPEG)


@pytest.mark.parametrize("document, expected", [
    ("1098765439", REJECTED), ("1098765438", PENDING), ("1098765432", APPROVED), ("AB12340", APPROVED),
])
async def test_el_simulado_decide_por_el_ultimo_digito_del_documento(document, expected):
    result = await verify(SimulatedIdentityVerifier(), document)

    assert result.status == expected
    assert result.reference.startswith("sim-")


async def test_cada_verificacion_tiene_su_propia_referencia_y_la_diferida_aprueba():
    verifier = SimulatedIdentityVerifier()

    first, second = await verify(verifier, "1098765438"), await verify(verifier, "1098765438")
    decided = await verifier.decide(first.reference)

    assert first.reference != second.reference
    assert decided == VerificationResult(APPROVED, first.reference)


async def test_fuera_de_desarrollo_el_simulado_nunca_aprueba_ni_rechaza():
    verifier = SimulatedIdentityVerifier(decides=False)

    results = [await verify(verifier, document) for document in ("1098765432", "1098765439")]
    decided = await verifier.decide("sim-ref")

    assert [result.status for result in results] == [PENDING, PENDING]
    assert decided.status == PENDING


async def test_un_proveedor_lento_o_caido_deja_la_verificacion_pendiente():
    class Broken:
        async def decide(self, reference):
            raise ConnectionError("caído")

    slow = SimulatedIdentityVerifier(delay_seconds=1.0)

    started = asyncio.get_running_loop().time()
    late = await InsuredRegistrationService._verify(slow, lambda: verify(slow, "1098765432"))
    elapsed = asyncio.get_running_loop().time() - started
    broken = await InsuredRegistrationService._verify(Broken(), lambda: Broken().decide("x"))

    assert late == VerificationResult(PENDING)
    assert elapsed < 0.9
    assert broken == VerificationResult(PENDING)


def test_el_registro_admite_cinco_intentos_por_hora_por_documento():
    four = [NOW - timedelta(minutes=m) for m in (1, 2, 3, 4)]
    five = four + [NOW - timedelta(minutes=50)]
    old = [NOW - timedelta(minutes=61)] * 10

    assert policy.registration_retry_after(four + old, NOW) is None
    assert policy.registration_retry_after(five, NOW) == 10 * 60


def test_el_seudonimo_del_documento_no_lleva_el_numero_y_es_estable():
    subject = InsuredRegistrationService._subject("CC", "1098765432")

    assert "1098765432" not in subject
    assert subject == InsuredRegistrationService._subject("CC", "1098765432")
    assert subject != InsuredRegistrationService._subject("CE", "1098765432")
