"""Latencia de las rutas críticas en memoria, por réplica (FE-WEB-07).
Cada pedido se clasifica por la plantilla de su ruta en un grupo fijo; nunca se guarda la URL, ni
identificadores, ni datos personales: solo la duración en milisegundos."""
import logging
import math
import threading
import time
from collections import deque
from typing import Deque, Dict, List, Optional, Sequence

from app.core.config import settings

logger = logging.getLogger("solventa.metrics")

WINDOW = 500
GROUPS = ("quote", "issue", "payment", "claim")
# Meta de cada grupo; la de la cotización es HA-01.
TARGETS_MS = {"quote": 400, "issue": 1000, "payment": 1000, "claim": 1000}

ROUTE_GROUPS = {
    ("POST", "/quote-edge/quotes"): "quote",
    ("POST", "/experience/quotes"): "quote",
    ("POST", "/quote-edge/policies"): "issue",
    ("POST", "/experience/policies"): "issue",
    ("POST", "/experience/payments/charge"): "payment",
    ("POST", "/insured/claims"): "claim",
}


def route_group(scope: dict) -> Optional[str]:
    """Grupo de la ruta que atendió el pedido, según su plantilla; None si no es crítica."""
    path = getattr(scope.get("route"), "path", None)
    if not isinstance(path, str):
        return None
    if path.startswith(settings.API_V1_STR):
        path = path[len(settings.API_V1_STR):]
    return ROUTE_GROUPS.get((scope.get("method"), path))


def percentile(samples: Sequence[float], p: float) -> Optional[float]:
    """Percentil por rango más cercano; None sin muestras."""
    if not samples:
        return None
    ordered = sorted(samples)
    rank = max(1, math.ceil(p / 100 * len(ordered)))
    return ordered[min(rank, len(ordered)) - 1]


class LatencyWindow:
    """Las últimas `size` duraciones de cada grupo; seguro con varios hilos."""

    def __init__(self, size: int = WINDOW):
        self._samples: Dict[str, Deque[float]] = {group: deque(maxlen=size) for group in GROUPS}
        self._lock = threading.Lock()

    def record(self, group: str, duration_ms: float) -> None:
        samples = self._samples.get(group)
        if samples is None:
            return
        with self._lock:
            samples.append(duration_ms)

    def snapshot(self) -> Dict[str, List[float]]:
        with self._lock:
            return {group: list(samples) for group, samples in self._samples.items()}

    def clear(self) -> None:
        with self._lock:
            for samples in self._samples.values():
                samples.clear()


latency_window = LatencyWindow()


class LatencyMiddleware:
    """Middleware ASGI puro: mide de punta a punta y, si la ruta es crítica, guarda la duración."""

    def __init__(self, app, window: LatencyWindow = latency_window):
        self.app = app
        self.window = window

    async def __call__(self, scope, receive, send):
        if scope["type"] != "http":
            await self.app(scope, receive, send)
            return
        start = time.perf_counter()
        try:
            await self.app(scope, receive, send)
        finally:
            self._observe(scope, start)

    def _observe(self, scope: dict, start: float) -> None:
        # La medición nunca rompe el pedido.
        try:
            group = route_group(scope)
            if group is not None:
                self.window.record(group, (time.perf_counter() - start) * 1000)
        except Exception as exc:
            logger.warning("No se pudo registrar la latencia (%s)", type(exc).__name__)
