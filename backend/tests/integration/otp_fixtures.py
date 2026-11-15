"""Códigos de un solo uso reales para las pruebas: se piden al endpoint y se usa el código de prueba que devuelve."""
from httpx import AsyncClient

TEST_PHONE = "3000000000"


async def consent_code(client: AsyncClient, headers: dict, document: str) -> str:
    response = await client.post(
        "/api/v1/experience/consent/otp-request", headers=headers,
        json={"document_number": document, "document_type": "CC", "phone": TEST_PHONE},
    )
    assert response.status_code == 200
    return response.json()["debug_code"]


async def signature_code(client: AsyncClient, headers: dict, document: str = "1020304050") -> str:
    response = await client.post(
        "/api/v1/experience/policies/otp-request", headers=headers,
        json={"document_number": document, "document_type": "CC", "phone": TEST_PHONE},
    )
    assert response.status_code == 200
    return response.json()["debug_code"]
