"""Tope del cuerpo por ruta antes de leer el multipart (mejora FE-MOV-01)."""
import pytest
from httpx import ASGITransport, AsyncClient

from app.core.middleware import BodyLimit, BodySizeLimitMiddleware

LIMITS = [
    BodyLimit("POST", "/api/v1/auth/register", 100),
    BodyLimit("POST", "/api/v1/insured/claims/{claim_number}/documents", 200),
]


async def echo(scope, receive, send):
    size = 0
    while True:
        message = await receive()
        size += len(message.get("body", b""))
        if not message.get("more_body"):
            break
    await send({"type": "http.response.start", "status": 200, "headers": [(b"content-type", b"text/plain")]})
    await send({"type": "http.response.body", "body": str(size).encode()})


async def post(path: str, body: bytes, method: str = "POST", stream: bool = False):
    transport = ASGITransport(app=BodySizeLimitMiddleware(echo, limits=LIMITS))
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        async def chunks():
            for start in range(0, len(body), 50):
                yield body[start:start + 50]
        return await client.request(method, path, content=chunks() if stream else body)


@pytest.mark.parametrize("path, size, expected", [
    ("/api/v1/auth/register", 100, 200),
    ("/api/v1/auth/register", 101, 413),
    ("/api/v1/insured/claims/SIN-2026-00001/documents", 200, 200),
    ("/api/v1/insured/claims/SIN-2026-00001/documents", 201, 413),
    ("/api/v1/insured/claims/SIN-2026-00001/otra/documents", 500, 200),
    ("/api/v1/auth/register/otp", 500, 200),
], ids=["registro-al-tope", "registro-pasado", "documentos-al-tope", "documentos-pasado", "otro-segmento", "otra-ruta"])
@pytest.mark.parametrize("stream", [False, True], ids=["con-content-length", "sin-content-length"])
async def test_aplica_el_tope_de_la_ruta_que_coincide_con_el_patron(path, size, expected, stream):
    response = await post(path, b"0" * size, stream=stream)

    assert response.status_code == expected
    if expected == 413:
        assert response.json()["detail"] == "La solicitud supera el tamaño permitido."


async def test_otro_metodo_en_la_misma_ruta_no_tiene_tope():
    response = await post("/api/v1/auth/register", b"0" * 500, method="PUT")

    assert response.status_code == 200


async def test_sin_content_length_responde_413_aunque_un_middleware_interno_envuelva_el_corte():
    """Como BaseHTTPMiddleware, la app lee el cuerpo dentro de un grupo de tareas que envuelve la excepción."""
    import anyio

    async def wrapped(scope, receive, send):
        async with anyio.create_task_group() as group:
            group.start_soon(echo, scope, receive, send)

    transport = ASGITransport(app=BodySizeLimitMiddleware(wrapped, limits=LIMITS))
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        async def chunks():
            for _ in range(10):
                yield b"0" * 50
        response = await client.post("/api/v1/auth/register", content=chunks())

    assert response.status_code == 413
