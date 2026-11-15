"""Percentiles, ventana de latencia, clasificación de rutas y periodos del tablero operativo (FE-WEB-07)."""
import threading
import time
from datetime import datetime, timezone
from types import SimpleNamespace

import pytest

from app.core.metrics import LatencyWindow, percentile, route_group
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
