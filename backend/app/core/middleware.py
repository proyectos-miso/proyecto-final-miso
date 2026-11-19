"""Middleware y variable de contexto para multi-tenancy."""
import json
import re
from contextvars import ContextVar
from dataclasses import dataclass, field
from typing import Iterable, Optional, Pattern
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


@dataclass(frozen=True)
class BodyLimit:
    """Tope del cuerpo de una ruta: el método, el patrón con sus parámetros entre llaves y los bytes permitidos."""

    method: str
    path: str
    max_bytes: int
    regex: Pattern = field(init=False, repr=False, compare=False)

    def __post_init__(self):
        parts = re.split(r"(\{[^/{}]+\})", self.path)
        pattern = "".join("[^/]+" if part.startswith("{") else re.escape(part) for part in parts)
        object.__setattr__(self, "regex", re.compile(pattern))

    def matches(self, method: str, path: str) -> bool:
        return method == self.method and self.regex.fullmatch(path) is not None


class BodySizeLimitMiddleware:
    """Tope del cuerpo por ruta antes de leer el multipart (mejora FE-MOV-01): Starlette parsearía el formulario
    completo, con las partes grandes en archivos temporales, antes de que la ruta pueda medir cada archivo.
    Rechaza por `Content-Length` y, si no viene, cuenta los bytes a medida que llegan; responde 413."""

    def __init__(self, app, limits: Iterable[BodyLimit]):
        self.app = app
        self.limits = tuple(limits)

    def _limit_for(self, scope) -> Optional[int]:
        if scope["type"] != "http":
            return None
        method, path = scope.get("method", ""), scope.get("path", "")
        return next((rule.max_bytes for rule in self.limits if rule.matches(method, path)), None)

    async def __call__(self, scope, receive, send):
        limit = self._limit_for(scope)
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
        except Exception:
            # Un BaseHTTPMiddleware interno envuelve el corte en un ExceptionGroup: basta saber que se pasó del tope.
            if not exceeded:
                raise
        if exceeded and not started:
            await self._too_large(scope, receive, send)

    @staticmethod
    async def _too_large(scope, receive, send):
        response = JSONResponse(
            status_code=413,
            content={"detail": "La solicitud supera el tamaño permitido.", "code": "INVALID_PHOTO"},
        )
        await response(scope, receive, send)
