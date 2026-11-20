"""Pasarela de pagos simulada: no hay integración real ni datos de tarjeta, solo tokens (HA-15)."""
from typing import Dict, Optional

# Tokens de prueba que el banco rechaza, con su motivo.
DECLINED_TOKENS = {"tok_insufficient_funds": "fondos insuficientes"}
# Documento de prueba cuya cuenta el banco rechaza al pagar.
DECLINED_PAYOUT_DOCUMENTS = {"9999999999": "cuenta del beneficiario inactiva"}


class GatewayUnavailableError(Exception):
    pass


class SimulatedGateway:
    """Aprueba todo token salvo los rechazados de prueba; la misma clave devuelve su resultado sin cobrar otra vez."""

    def __init__(self):
        self.charges: Dict[str, Optional[str]] = {}

    async def charge(self, idempotency_key: str, amount: float, token: str) -> Optional[str]:
        """Motivo del rechazo, o None si el cargo se aprobó."""
        return self.charges.setdefault(idempotency_key, DECLINED_TOKENS.get(token))

    async def payout(self, idempotency_key: str, amount: float, beneficiary_document: str) -> Optional[str]:
        """Pago al beneficiario: se aprueba salvo el documento de prueba rechazado; la misma clave no paga dos veces."""
        return self.charges.setdefault(idempotency_key, DECLINED_PAYOUT_DOCUMENTS.get(beneficiary_document))
