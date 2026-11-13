"""Pruebas para TC-11: supresión definitiva del parámetro simulated_delay en cotización (RC-09, CTL-08)."""
import time
import pytest
from httpx import AsyncClient

from app.core.security import create_access_token


def advisor_headers() -> dict:
    return {"Authorization": f"Bearer {create_access_token(subject='asesor@solventa.com', role='advisor')}"}


@pytest.mark.integration
async def test_quote_endpoint_ignores_simulated_delay_and_openapi_excludes_it(
    async_client: AsyncClient, postgres_session
):
    """TC-11: simulated_delay no introduce demora y no aparece en los parámetros de la especificación OpenAPI."""
    # 1. Enviar cotización con simulated_delay=5
    start_time = time.monotonic()
    resp = await async_client.post(
        "/api/v1/experience/quotes?simulated_delay=5",
        headers=advisor_headers(),
        json={
            "document_type": "CC",
            "document_number": "1020304050",
            "birth_date": "1990-05-15",
            "insured_amount": 100000000.0,
        },
    )
    elapsed = time.monotonic() - start_time
    assert resp.status_code == 200
    # No debe haber tardado 5 segundos
    assert elapsed < 1.5, f"La respuesta tardó {elapsed}s, simulated_delay aún introduce demoras"

    # 2. Consultar OpenAPI y verificar que simulated_delay no existe en /api/v1/experience/quotes
    openapi_resp = await async_client.get("/openapi.json")
    assert openapi_resp.status_code == 200
    openapi = openapi_resp.json()

    exp_quotes_params = (
        openapi.get("paths", {})
        .get("/api/v1/experience/quotes", {})
        .get("post", {})
        .get("parameters", [])
    )
    param_names = [p["name"] for p in exp_quotes_params]
    assert "simulated_delay" not in param_names

    edge_quotes_params = (
        openapi.get("paths", {})
        .get("/api/v1/quote-edge/quotes", {})
        .get("post", {})
        .get("parameters", [])
    )
    edge_names = [p["name"] for p in edge_quotes_params]
    assert "simulated_delay" not in edge_names
