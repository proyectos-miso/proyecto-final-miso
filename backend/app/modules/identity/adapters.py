"""Proveedor de verificación de identidad simulado: no hay contrato con uno real (mejora FE-MOV-01, ADR 0031)."""
import asyncio
import uuid
from functools import lru_cache

from app.core.config import settings
from app.modules.identity.ports import APPROVED, PENDING, REJECTED, VerificationResult


class SimulatedIdentityVerifier:
    """Determinístico para pruebas y demo: documento terminado en 9 se rechaza, en 8 queda pendiente.
    Fuera de desarrollo nunca aprueba ni rechaza: todo queda pendiente hasta tener un proveedor real."""

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


@lru_cache
def get_identity_verifier() -> SimulatedIdentityVerifier:
    return SimulatedIdentityVerifier(decides=settings.debug_enabled)
