"""Puerto del proveedor que compara la foto del documento con la selfie (mejora FE-MOV-01, ADR 0031)."""
from dataclasses import dataclass
from typing import Optional, Protocol

APPROVED = "APPROVED"
PENDING = "PENDING"
REJECTED = "REJECTED"


@dataclass(frozen=True)
class VerificationResult:
    status: str
    reference: Optional[str] = None


class IdentityVerifier(Protocol):
    async def verify(
        self, document_type: str, document_number: str, full_name: str, document_front: bytes, selfie: bytes
    ) -> VerificationResult: ...

    async def decide(self, reference: str) -> VerificationResult: ...
