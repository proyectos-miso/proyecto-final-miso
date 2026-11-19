"""Proveedor de verificación de identidad simulado: no hay contrato con uno real (mejora FE-MOV-01, ADR 0031)."""
import asyncio
import uuid

from app.core.config import settings
from app.modules.identity.ports import APPROVED, PENDING, REJECTED, VerificationResult


class SimulatedIdentityVerifier:
    """Determinístico para pruebas y demo: documento terminado en 9 se rechaza, en 8 queda pendiente.
    Solo decide en development, local y test; en cualquier otro ambiente todo queda pendiente hasta tener un proveedor real."""

    def __init__(self, decides: bool = True, delay_seconds: float = 0.0):
        self._decides = decides
        self._delay = delay_seconds

    async def verify(
        self, document_type: str, document_number: str, full_name: str, document_front: bytes, selfie: bytes
    ) -> VerificationResult:
        if self._delay:
            await asyncio.sleep(self._delay)
        reference = f"sim-{uuid.uuid4().hex}"
        if not self._decides or document_number.endswith("8"):
            return VerificationResult(PENDING, reference)
        if document_number.endswith("9"):
            return VerificationResult(REJECTED, reference)
        return VerificationResult(APPROVED, reference)

    async def decide(self, reference: str) -> VerificationResult:
        return VerificationResult(APPROVED if self._decides else PENDING, reference)


def get_identity_verifier() -> SimulatedIdentityVerifier:
    """Lista de ambientes permitidos, no «todo menos production»: staging, prod o un nombre mal escrito nunca aprueban."""
    return SimulatedIdentityVerifier(decides=settings.simulated_identity_decides)
