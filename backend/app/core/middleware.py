"""Middleware y variable de contexto para multi-tenancy."""
import json
from contextvars import ContextVar
from typing import Optional
from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request
from starlette.responses import Response, JSONResponse

# ContextVar segura para aislamiento multi-tenant en peticiones asíncronas (ASR-06 / HA-14)
tenant_context: ContextVar[Optional[str]] = ContextVar("tenant_context", default=None)


class TenantContextMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request: Request, call_next):
        partner_id = request.headers.get("X-Partner-Id")
        token = tenant_context.set(partner_id)
        try:
            response = await call_next(request)
            return response
        finally:
            tenant_context.reset(token)



class _BodyTooLarge(Exception):
    pass


class BodySizeLimitMiddleware:
    """Tope del cuerpo por ruta antes de leer el multipart (mejora FE-MOV-01): Starlette parsearía el formulario
    completo, con las partes grandes en archivos temporales, antes de que la ruta pueda medir cada foto.
    Rechaza por `Content-Length` y, si no viene, cuenta los bytes a medida que llegan; responde 413."""

    def __init__(self, app, limits: dict):
        self.app = app
        self.limits = limits

    async def __call__(self, scope, receive, send):
        limit = self.limits.get(scope.get("path")) if scope["type"] == "http" else None
        if limit is None:
            await self.app(scope, receive, send)
            return
        length = dict(scope["headers"]).get(b"content-length")
        if length is not None and (not length.isdigit() or int(length) > limit):
            await self._too_large(scope, receive, send)
            return
        received = 0
        exceeded = False
        started = False

        async def limited_receive():
            nonlocal received, exceeded
            message = await receive()
            if message["type"] == "http.request":
                received += len(message.get("body", b""))
                if received > limit:
                    exceeded = True
                    raise _BodyTooLarge()
            return message

        async def guarded_send(message):
            nonlocal started
            if exceeded:
                return
            if message["type"] == "http.response.start":
                started = True
            await send(message)

        try:
            await self.app(scope, limited_receive, guarded_send)
        except _BodyTooLarge:
            pass
        if exceeded and not started:
            await self._too_large(scope, receive, send)

    @staticmethod
    async def _too_large(scope, receive, send):
        response = JSONResponse(
            status_code=413,
            content={"detail": "La solicitud supera el tamaño permitido.", "code": "INVALID_PHOTO"},
        )
        await response(scope, receive, send)
