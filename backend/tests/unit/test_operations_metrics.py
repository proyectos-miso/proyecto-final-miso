"""Percentiles, ventana de latencia, clasificación de rutas y periodos del tablero operativo (FE-WEB-07)."""
import threading
import time
from datetime import datetime, timezone
from types import SimpleNamespace

import pytest
from fastapi import FastAPI, HTTPException, Response
from httpx import ASGITransport, AsyncClient

from app.core.metrics import LatencyMiddleware, LatencyWindow, percentile, route_group
from app.modules.reports import dashboard
from app.modules.reports.dashboard import latency_groups, period_start, safe_ratio

pytestmark = pytest.mark.unit


def scope(method: str, path: str) -> dict:
    return {"type": "http", "method": method, "route": SimpleNamespace(path=path), "path": "/no-se-usa"}


def test_percentil_por_rango_mas_cercano():
    samples = [float(value) for value in range(1, 101)]
    assert percentile(samples, 50) == 50.0
    assert percentile(samples, 95) == 95.0
    assert percentile(samples, 99) == 99.0
    assert percentile([7.0], 99) == 7.0
    assert percentile([3.0, 1.0, 2.0], 50) == 2.0


def test_percentil_sin_muestras_es_nulo():
    assert percentile([], 95) is None


def test_la_ruta_se_clasifica_por_su_plantilla_con_o_sin_prefijo():
    assert route_group(scope("POST", "/api/v1/quote-edge/quotes")) == "quote"
    assert route_group(scope("POST", "/experience/quotes")) == "quote"
    assert route_group(scope("POST", "/quote-edge/policies")) == "issue"
    assert route_group(scope("POST", "/api/v1/experience/policies")) == "issue"
    assert route_group(scope("POST", "/experience/payments/charge")) == "payment"
    assert route_group(scope("POST", "/insured/claims")) == "claim"


def test_rutas_no_criticas_o_sin_plantilla_no_se_clasifican():
    assert route_group(scope("GET", "/experience/quotes")) is None
    assert route_group(scope("POST", "/experience/policies/{policy_number}/renewal")) is None
    assert route_group(scope("GET", "/health")) is None
    assert route_group({"type": "http", "method": "POST", "path": "/api/v1/insured/claims"}) is None


def test_la_ventana_guarda_solo_las_ultimas_muestras_de_grupos_conocidos():
    window = LatencyWindow(size=3)
    for value in (1.0, 2.0, 3.0, 4.0):
        window.record("quote", value)
    window.record("/api/v1/experience/claims/SIN-2026-00001", 9.0)

    snapshot = window.snapshot()

    assert snapshot["quote"] == [2.0, 3.0, 4.0]
    assert set(snapshot) == {"quote", "issue", "payment", "claim"}


def test_la_ventana_soporta_escrituras_concurrentes():
    window = LatencyWindow(size=500)

    def write():
        for _ in range(1000):
            window.record("claim", 1.0)

    threads = [threading.Thread(target=write) for _ in range(8)]
    for thread in threads:
        thread.start()
    for thread in threads:
        thread.join()

    assert len(window.snapshot()["claim"]) == 500


def test_la_latencia_se_compara_con_la_meta_por_p95():
    window = LatencyWindow()
    for value in [100.0] * 95 + [900.0] * 5:
        window.record("quote", value)
    for value in [1200.0] * 10:
        window.record("issue", value)

    groups = {group.group: group for group in latency_groups(window)}

    assert groups["quote"].p95_ms == 100.0 and groups["quote"].p99_ms == 900.0
    assert groups["quote"].within_target is True
    assert groups["issue"].within_target is False
    assert groups["payment"].within_target is None and groups["payment"].samples == 0


def test_los_periodos_empiezan_a_medianoche_de_colombia():
    now = datetime(2026, 11, 15, 3, 30, tzinfo=timezone.utc)  # 22:30 del 14 en Colombia

    assert period_start("today", now).isoformat() == "2026-11-14T00:00:00-05:00"
    assert period_start("7d", now).isoformat() == "2026-11-08T00:00:00-05:00"
    assert period_start("30d", now).isoformat() == "2026-10-16T00:00:00-05:00"


def test_division_segura_sin_cotizaciones():
    assert safe_ratio(0, 0) is None
    assert safe_ratio(1, 3) == 0.3333


def test_clasificar_y_registrar_una_muestra_cuesta_menos_de_50_microsegundos():
    window = LatencyWindow()
    request = scope("POST", "/api/v1/quote-edge/quotes")
    rounds = 10_000

    start = time.perf_counter()
    for _ in range(rounds):
        window.record(route_group(request), 1.0)
    per_call_us = (time.perf_counter() - start) / rounds * 1_000_000

    assert per_call_us < 50


def critical_routes_app(window: LatencyWindow) -> FastAPI:
    app = FastAPI()
    app.add_middleware(LatencyMiddleware, window=window)

    @app.post("/api/v1/quote-edge/quotes")
    async def quote():
        return {"ok": True}

    @app.post("/api/v1/insured/claims")
    async def claim():
        raise HTTPException(status_code=401, detail="Sesión inválida.")

    @app.post("/api/v1/experience/policies")
    async def issue():
        return Response(status_code=500)

    @app.post("/api/v1/experience/payments/charge")
    async def charge():
        return Response(status_code=400)

    return app


async def test_solo_se_registran_las_respuestas_por_debajo_de_400():
    window = LatencyWindow()
    transport = ASGITransport(app=critical_routes_app(window))
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        assert (await client.post("/api/v1/quote-edge/quotes")).status_code == 200
        assert (await client.post("/api/v1/insured/claims")).status_code == 401
        assert (await client.post("/api/v1/experience/policies")).status_code == 500
        assert (await client.post("/api/v1/experience/payments/charge")).status_code == 400

    counts = {group: len(samples) for group, samples in window.snapshot().items()}
    assert counts == {"quote": 1, "issue": 0, "payment": 0, "claim": 0}


async def test_las_comprobaciones_corren_en_hilos_daemon_y_sin_hilos_libres_fallan_en_seguida(monkeypatch):
    seen = []
    check = dashboard._in_thread(lambda: seen.append(threading.current_thread().daemon))
    await check()
    assert seen == [True]

    monkeypatch.setattr(dashboard, "_check_slots", threading.BoundedSemaphore(1))
    dashboard._check_slots.acquire()
    with pytest.raises(dashboard.NoCheckSlotError):
        await dashboard._in_thread(lambda: None)()


async def test_sin_hilos_libres_la_comprobacion_queda_sin_comprobar_y_no_caida(monkeypatch):
    monkeypatch.setattr(dashboard, "_check_slots", threading.BoundedSemaphore(1))
    dashboard._check_slots.acquire()

    status = await dashboard.run_check("s3", dashboard._in_thread(lambda: None))

    assert status == dashboard.DependencyStatus(name="s3", status="unchecked", latency_ms=None)


async def test_una_falla_del_middleware_no_rompe_el_pedido(monkeypatch):
    """La ruta crítica responde 200, así que el middleware sí intenta registrar y el registro falla."""
    window = LatencyWindow()
    attempts = []

    def boom(*args, **kwargs):
        attempts.append(1)
        raise RuntimeError("falla de la medición")

    monkeypatch.setattr(window, "record", boom)
    transport = ASGITransport(app=critical_routes_app(window))
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        response = await client.post("/api/v1/quote-edge/quotes")

    assert response.status_code == 200
    assert response.json() == {"ok": True}
    assert attempts == [1]


def test_las_sondas_tienen_clientes_propios_con_un_intento_y_un_segundo(monkeypatch):
    monkeypatch.setattr(dashboard.settings, "AWS_ENDPOINT_URL", "http://floci.test:4566")
    dashboard.s3_probe_client.cache_clear()
    dashboard.sqs_probe_client.cache_clear()
    try:
        for client in (dashboard.s3_probe_client(), dashboard.sqs_probe_client()):
            config = client.meta.config
            assert (config.connect_timeout, config.read_timeout) == (1, 1)
            assert config.retries["total_max_attempts"] == 1
            assert client.meta.endpoint_url == "http://floci.test:4566"
            assert client.meta.region_name == dashboard.settings.AWS_REGION
        assert dashboard.s3_probe_client() is dashboard.s3_probe_client()
    finally:
        dashboard.s3_probe_client.cache_clear()
        dashboard.sqs_probe_client.cache_clear()
