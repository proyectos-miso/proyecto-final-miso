"""Tablero operativo del portal de Solventa (HOTFIX-tablero-operativo, FE-WEB-07)."""
import asyncio
import time
from datetime import datetime, timedelta, timezone

import pytest
from httpx import ASGITransport, AsyncClient
from sqlalchemy import insert, text

from app.bff.experience import operations_router
from app.core.database import get_db_session
from app.core.metrics import latency_window
from app.core.outbox import OutboxMessageDB
from app.main import app
from app.modules.claims.models import ClaimDB
from app.modules.policy.models import PolicyDB
from app.modules.rating.models import QuoteDB
from app.modules.reports import dashboard
from tests.integration.policy_search_fixtures import headers, seed_partners_and_users, tier

pytestmark = pytest.mark.integration

ANALISTA = "operaciones@solventa.example"
DEL_ALIADO = "analista@bancoaliado.example"
INDICATORS = "/api/v1/experience/operations/indicators"
FAKE_SECRET = "detalle-interno-que-no-debe-salir"


async def ok() -> None:
    return None


@pytest.fixture(autouse=True)
def dependencies_ok():
    """S3, SQS y Firebase simulados: ninguna prueba sale a la red."""
    app.dependency_overrides[dashboard.get_dependency_checks] = lambda: {"s3": ok, "sqs": ok, "firebase": ok}
    latency_window.clear()
    dashboard.clear_cache()
    yield
    app.dependency_overrides.pop(dashboard.get_dependency_checks, None)
    latency_window.clear()
    dashboard.clear_cache()


@pytest.fixture
async def seeded(postgres_session):
    await seed_partners_and_users(postgres_session)
    return postgres_session


def quote_row(number: int, created_at: datetime) -> dict:
    return {
        "quote_id": f"QUO-OP-{number:05d}",
        "partner_id": None,
        "document_number": "1020304050",
        "status": "active",
        "score_applied": 700.0,
        "tiers": [tier()],
        "created_at": created_at,
        "expires_at": created_at + timedelta(days=15),
    }


def policy_row(number: int, quote: dict) -> dict:
    issued = quote["created_at"] + timedelta(minutes=1)
    return {
        "policy_number": f"POL-2026-{number:05d}",
        "quote_id": quote["quote_id"],
        "partner_id": None,
        "insured_document": quote["document_number"],
        "beneficiaries": [],
        "conditions": tier(),
        "status": "ACTIVE",
        "issued_at": issued,
        "valid_until": issued + timedelta(days=365),
    }


async def seed_activity(session) -> None:
    """Hoy 4 cotizaciones y 2 pólizas; hace 3 días 2 y 1; hace 20 días 1 sin póliza; hace 40 días 1 con póliza."""
    now = datetime.now(timezone.utc)
    today = dashboard.period_start("today", now)
    middle_of_today = today + (now - today) / 2
    moments = [middle_of_today] * 4 + [today - timedelta(days=3)] * 2 + [today - timedelta(days=20), today - timedelta(days=40)]
    quotes = [quote_row(i, moment) for i, moment in enumerate(moments, start=1)]
    policies = [policy_row(i, quotes[index]) for i, index in enumerate((0, 1, 4, 7), start=1)]
    await session.execute(insert(QuoteDB), quotes)
    await session.execute(insert(PolicyDB), policies)
    statuses = ["REPORTED", "IN_REVIEW", "SETTLED", "REJECTED", "PAID"]
    session.add_all(ClaimDB(claim_number=f"SIN-2026-{i:05d}", policy_number="POL-2026-00001", status=status,
                            reported_at=now - timedelta(days=1)) for i, status in enumerate(statuses, start=1))
    session.add_all([
        OutboxMessageDB(aggregate_id="SIN-2026-00001", event_type="ClaimFiled", payload={}, status="PENDING"),
        OutboxMessageDB(aggregate_id="SIN-2026-00002", event_type="ClaimFiled", payload={}, status="PENDING"),
        OutboxMessageDB(aggregate_id="SIN-2026-00003", event_type="ClaimFiled", payload={}, status="PUBLISHED"),
    ])
    await session.commit()


async def test_sin_sesion_401_y_el_analista_de_un_socio_o_el_asesor_403(async_client: AsyncClient, seeded):
    assert (await async_client.get(INDICATORS)).status_code == 401
    assert (await async_client.get(INDICATORS, headers=headers(DEL_ALIADO))).status_code == 403
    assert (await async_client.get(INDICATORS, headers=headers("asesor@solventa.example", "advisor"))).status_code == 403


async def test_indicadores_por_periodo_en_hora_de_colombia(async_client: AsyncClient, seeded):
    await seed_activity(seeded)

    bodies = {}
    for period in ("today", "7d", "30d"):
        response = await async_client.get(INDICATORS, params={"period": period}, headers=headers(ANALISTA))
        assert response.status_code == 200
        assert response.headers["cache-control"] == "no-store"
        bodies[period] = response.json()

    assert [bodies[p]["quotes"] for p in ("today", "7d", "30d")] == [4, 6, 7]
    assert [bodies[p]["policies_issued"] for p in ("today", "7d", "30d")] == [2, 3, 3]
    assert [bodies[p]["conversion"] for p in ("today", "7d", "30d")] == [0.5, 0.5, round(3 / 7, 4)]
    minutes = (datetime.fromisoformat(bodies["today"]["generated_at"]) - datetime.fromisoformat(bodies["today"]["since"])).total_seconds() / 60
    assert bodies["today"]["quotes_per_minute"] == round(4 / max(1.0, minutes), 4)
    assert bodies["today"]["open_claims"] == 2
    assert bodies["today"]["outbox_pending"] == 2
    assert bodies["today"]["since"].endswith("-05:00")
    assert bodies["7d"]["period"] == "7d"


async def test_sin_cotizaciones_la_conversion_es_nula_y_no_falla(async_client: AsyncClient, seeded):
    response = await async_client.get(INDICATORS, headers=headers(ANALISTA))

    assert response.status_code == 200
    body = response.json()
    assert body["period"] == "today"
    assert body["quotes"] == 0
    assert body["quotes_per_minute"] == 0
    assert body["conversion"] is None
    assert body["policies_issued"] == 0


async def test_un_periodo_desconocido_da_422(async_client: AsyncClient, seeded):
    response = await async_client.get(INDICATORS, params={"period": "1y"}, headers=headers(ANALISTA))
    assert response.status_code == 422


async def test_una_dependencia_caida_o_lenta_no_tumba_el_tablero_ni_expone_el_error(
    async_client: AsyncClient, seeded, monkeypatch
):
    monkeypatch.setattr(dashboard, "CHECK_TIMEOUT_SECONDS", 0.2)
    monkeypatch.setattr(dashboard, "DEGRADED_AFTER_MS", 20)

    async def broken() -> None:
        raise RuntimeError(f"AccessDenied {FAKE_SECRET}")

    async def hung() -> None:
        await asyncio.sleep(5)

    async def slow() -> None:
        await asyncio.sleep(0.05)

    app.dependency_overrides[dashboard.get_dependency_checks] = lambda: {"s3": broken, "sqs": hung, "firebase": slow}

    response = await async_client.get(INDICATORS, headers=headers(ANALISTA))

    assert response.status_code == 200
    states = {item["name"]: item for item in response.json()["dependencies"]}
    assert states["database"]["status"] in ("ok", "degraded")
    assert states["s3"]["status"] == "down"
    assert states["sqs"]["status"] == "down"
    assert states["sqs"]["latency_ms"] < 1000
    assert states["firebase"]["status"] == "degraded"
    assert all(set(item) == {"name", "status", "latency_ms"} for item in states.values())
    assert FAKE_SECRET not in response.text
    assert "AccessDenied" not in response.text
    assert response.json()["quotes"] == 0


async def test_firebase_sin_credenciales_queda_como_no_configurado(async_client: AsyncClient, seeded, monkeypatch):
    monkeypatch.setattr(dashboard, "get_sender", lambda: None)
    monkeypatch.setattr(dashboard.settings, "FIREBASE_CREDENTIALS_FILE", None)
    monkeypatch.setattr(dashboard.settings, "FIREBASE_CREDENTIALS_JSON", None)
    app.dependency_overrides[dashboard.get_dependency_checks] = lambda: {
        "firebase": dashboard._in_thread(dashboard._firebase)
    }

    response = await async_client.get(INDICATORS, headers=headers(ANALISTA))

    states = {item["name"]: item for item in response.json()["dependencies"]}
    assert states["firebase"] == {"name": "firebase", "status": "not_configured", "latency_ms": None}


@pytest.mark.parametrize("probe", ["SELECT pg_sleep(5)", "SELECT * FROM tabla_que_no_existe"], ids=["colgada", "falla"])
async def test_si_la_base_falla_despues_de_validar_al_analista_responde_sin_conteos_y_la_marca_caida(
    async_client: AsyncClient, seeded, monkeypatch, probe
):
    """Caso (a): el analista se valida con la base real; después la base se cuelga o falla."""
    monkeypatch.setattr(dashboard, "CHECK_TIMEOUT_SECONDS", 0.2)
    monkeypatch.setattr(dashboard, "DATABASE_PROBE", probe)

    started = time.perf_counter()
    response = await async_client.get(INDICATORS, headers=headers(ANALISTA))

    assert time.perf_counter() - started < 2
    assert response.status_code == 200
    body = response.json()
    assert body["dependencies"][0]["name"] == "database"
    assert body["dependencies"][0]["status"] == "down"
    assert body["quotes"] is None and body["conversion"] is None and body["outbox_pending"] is None
    assert "tabla_que_no_existe" not in response.text and "pg_sleep" not in response.text


class BrokenSession:
    """Sesión de una base caída del todo: cada lectura falla con un texto interno."""

    async def execute(self, *args, **kwargs):
        raise ConnectionError(FAKE_SECRET)

    async def rollback(self):
        return None

    async def close(self):
        return None


class HungSession(BrokenSession):
    async def execute(self, *args, **kwargs):
        await asyncio.sleep(30)


@pytest.mark.parametrize("session", [BrokenSession(), HungSession()], ids=["falla", "colgada"])
async def test_con_la_base_caida_del_todo_responde_503_generico_y_rapido(
    async_client: AsyncClient, seeded, monkeypatch, session
):
    """Caso (b): la lectura del analista no llega a la base; 503 genérico antes del límite más un margen."""
    monkeypatch.setattr(operations_router, "OPERATOR_TIMEOUT_SECONDS", 0.3)

    async def down_db():
        yield session

    real_db = app.dependency_overrides[get_db_session]
    app.dependency_overrides[get_db_session] = down_db
    try:
        started = time.perf_counter()
        response = await async_client.get(INDICATORS, headers=headers(ANALISTA))
        elapsed = time.perf_counter() - started
    finally:
        app.dependency_overrides[get_db_session] = real_db

    assert elapsed < 1.5
    assert response.status_code == 503
    assert response.json() == {"detail": operations_router.DASHBOARD_UNAVAILABLE}
    assert FAKE_SECRET not in response.text
    assert "ConnectionError" not in response.text and "Timeout" not in response.text


async def test_el_limite_de_la_base_deja_la_sesion_del_pedido_usable_y_cerrable(postgres_session, monkeypatch):
    """La sonda usa su propia conexión: cortar un `pg_sleep(2)` no deja la sesión con otra operación en curso."""
    monkeypatch.setattr(dashboard, "CHECK_TIMEOUT_SECONDS", 0.2)
    monkeypatch.setattr(dashboard, "DATABASE_PROBE", "SELECT pg_sleep(2)")

    status = await dashboard.run_check("database", dashboard.database_check(postgres_session))

    assert status.status == "down"
    assert status.latency_ms < 1000
    assert (await postgres_session.execute(text("SELECT 1"))).scalar() == 1
    monkeypatch.setattr(dashboard, "DATABASE_PROBE", "SELECT 1")
    assert (await dashboard.run_check("database", dashboard.database_check(postgres_session))).status in ("ok", "degraded")
    await postgres_session.close()


async def test_un_conteo_lento_da_null_sin_colgar(async_client: AsyncClient, seeded, monkeypatch):
    monkeypatch.setattr(dashboard, "COUNTS_TIMEOUT_MS", 100)
    monkeypatch.setattr(dashboard, "counts_statement", lambda start, now: text("SELECT pg_sleep(2)"))

    started = time.perf_counter()
    response = await async_client.get(INDICATORS, headers=headers(ANALISTA))

    assert time.perf_counter() - started < 1.5
    assert response.status_code == 200
    body = response.json()
    assert body["dependencies"][0]["status"] in ("ok", "degraded")
    assert body["quotes"] is None and body["open_claims"] is None
    assert "statement" not in response.text and "pg_sleep" not in response.text


async def test_dos_llamadas_seguidas_hacen_una_sola_comprobacion(async_client: AsyncClient, seeded):
    calls = []

    async def counted() -> None:
        calls.append(1)

    app.dependency_overrides[dashboard.get_dependency_checks] = lambda: {"s3": counted}

    first = await async_client.get(INDICATORS, headers=headers(ANALISTA))
    second = await async_client.get(INDICATORS, headers=headers(ANALISTA))

    assert first.status_code == second.status_code == 200
    assert len(calls) == 1
    assert first.json()["generated_at"] == second.json()["generated_at"]
    # Las sondas no dependen del periodo: otro periodo calcula sus conteos con el mismo estado de las dependencias.
    other = await async_client.get(INDICATORS, params={"period": "7d"}, headers=headers(ANALISTA))
    assert other.status_code == 200
    assert other.json()["generated_at"] != first.json()["generated_at"]
    assert len(calls) == 1
    # El caché no salta la autorización: el analista de un socio sigue recibiendo 403.
    assert (await async_client.get(INDICATORS, headers=headers(DEL_ALIADO))).status_code == 403


async def test_pedidos_simultaneos_de_varios_periodos_comparten_una_comprobacion_en_curso(
    async_client: AsyncClient, seeded
):
    calls = []

    async def slow() -> None:
        calls.append(1)
        await asyncio.sleep(0.2)

    app.dependency_overrides[dashboard.get_dependency_checks] = lambda: {"s3": slow, "sqs": slow}

    responses = await asyncio.gather(*(
        async_client.get(INDICATORS, params={"period": period}, headers=headers(ANALISTA))
        for period in ("today", "7d", "30d", "today")
    ))

    assert [response.status_code for response in responses] == [200, 200, 200, 200]
    assert len(calls) == 2
    assert {response.json()["dependencies"][1]["latency_ms"] for response in responses} == {
        responses[0].json()["dependencies"][1]["latency_ms"]
    }


async def test_sin_hilos_libres_la_dependencia_queda_sin_comprobar_y_no_se_guarda(
    async_client: AsyncClient, seeded, monkeypatch
):
    """Un cupo de hilos agotado no es una dependencia caída: «unchecked», sin latencia y sin caché."""
    calls = []
    real = dashboard._in_thread(lambda: calls.append(1))
    monkeypatch.setattr(dashboard, "_check_slots", dashboard.threading.BoundedSemaphore(1))
    dashboard._check_slots.acquire()
    app.dependency_overrides[dashboard.get_dependency_checks] = lambda: {"s3": real}

    first = await async_client.get(INDICATORS, headers=headers(ANALISTA))

    assert first.status_code == 200
    states = {item["name"]: item for item in first.json()["dependencies"]}
    assert states["s3"] == {"name": "s3", "status": "unchecked", "latency_ms": None}
    assert states["database"]["status"] in ("ok", "degraded")
    assert first.json()["quotes"] == 0
    assert calls == []

    dashboard._check_slots.release()
    second = await async_client.get(INDICATORS, headers=headers(ANALISTA))

    states = {item["name"]: item for item in second.json()["dependencies"]}
    assert states["s3"]["status"] in ("ok", "degraded")
    assert calls == [1]
    assert second.json()["generated_at"] != first.json()["generated_at"]


async def test_la_latencia_por_grupo_se_compara_con_su_meta(async_client: AsyncClient, seeded):
    for value in (120.0, 250.0, 380.0):
        latency_window.record("quote", value)

    response = await async_client.get(INDICATORS, headers=headers(ANALISTA))

    groups = {item["group"]: item for item in response.json()["latency"]}
    assert set(groups) == {"quote", "issue", "payment", "claim"}
    assert groups["quote"]["samples"] == 3
    assert groups["quote"]["target_ms"] == 400
    assert (groups["quote"]["p50_ms"], groups["quote"]["p95_ms"], groups["quote"]["p99_ms"]) == (250.0, 380.0, 380.0)
    assert groups["quote"]["within_target"] is True
    assert groups["claim"] == {"group": "claim", "samples": 0, "p50_ms": None, "p95_ms": None, "p99_ms": None,
                               "target_ms": 1000, "within_target": None}


async def test_el_middleware_no_guarda_respuestas_de_error_ni_identificadores(async_client: AsyncClient, seeded):
    """Las rutas críticas sin sesión o con el cuerpo inválido responden 4xx y no cuentan; las demás nunca cuentan."""
    responses = [
        await async_client.post("/api/v1/quote-edge/quotes", json={"document_number": "1020304050"}),
        await async_client.post("/api/v1/experience/policies", json={}, headers=headers("asesor@solventa.example", "advisor")),
        await async_client.post("/api/v1/experience/payments/charge", json={}),
        await async_client.post("/api/v1/insured/claims", json={}),
    ]
    await async_client.get("/api/v1/experience/claims/SIN-2026-00001", headers=headers(ANALISTA))
    await async_client.post("/api/v1/experience/policies/POL-2026-00001/renewal", headers=headers(ANALISTA))
    await async_client.get("/health")

    snapshot = latency_window.snapshot()

    assert all(response.status_code >= 400 for response in responses)
    assert set(snapshot) == {"quote", "issue", "payment", "claim"}
    assert {group: len(samples) for group, samples in snapshot.items()} == {"quote": 0, "issue": 0, "payment": 0, "claim": 0}
    stored = repr(latency_window.__dict__)
    for leaked in ("1020304050", "SIN-2026-00001", "POL-2026-00001", "/api/v1", "asesor"):
        assert leaked not in stored


async def test_una_cotizacion_exitosa_en_la_aplicacion_completa_deja_una_muestra(async_client: AsyncClient, seeded):
    """Con la pila de `app.main` (CORS y `TenantContextMiddleware` incluidos) la cotización 200 sí cuenta."""
    response = await async_client.post(
        "/api/v1/experience/quotes",
        headers={**headers("asesor@solventa.example", "advisor"), "Origin": "http://localhost:4200"},
        json={
            "document_type": "CC",
            "document_number": "1020304050",
            "birth_date": "1990-05-15",
            "insured_amount": 100000000.0,
            "occupation_risk": 2,
        },
    )

    assert response.status_code == 200
    assert {group: len(samples) for group, samples in latency_window.snapshot().items()} == {
        "quote": 1, "issue": 0, "payment": 0, "claim": 0}
    groups = {item["group"]: item for item in (await async_client.get(INDICATORS, headers=headers(ANALISTA))).json()["latency"]}
    assert groups["quote"]["samples"] == 1
    assert "1020304050" not in repr(latency_window.__dict__)


async def test_la_base_lenta_al_validar_al_analista_da_503_y_el_pool_sigue_sirviendo(
    async_client: AsyncClient, seeded, monkeypatch
):
    """Caso (b) con la base real: la lectura del analista se corta a los 0,2 s y el siguiente pedido responde bien."""
    monkeypatch.setattr(operations_router, "OPERATOR_TIMEOUT_SECONDS", 0.2)
    real_scope = operations_router.operations_scope

    async def slow_scope(claims, db):
        await db.execute(text("SELECT pg_sleep(1)"))
        return await real_scope(claims, db)

    monkeypatch.setattr(operations_router, "operations_scope", slow_scope)
    started = time.perf_counter()
    response = await async_client.get(INDICATORS, headers=headers(ANALISTA))
    elapsed = time.perf_counter() - started

    assert response.status_code == 503
    assert response.json() == {"detail": operations_router.DASHBOARD_UNAVAILABLE}
    assert elapsed < 1
    assert "pg_sleep" not in response.text

    monkeypatch.setattr(operations_router, "operations_scope", real_scope)
    for _ in range(3):
        after = await async_client.get(INDICATORS, headers=headers(ANALISTA))
        assert after.status_code == 200
        assert after.json()["dependencies"][0]["status"] in ("ok", "degraded")
        assert after.json()["quotes"] == 0
        dashboard.clear_cache()


async def test_un_error_que_no_es_de_la_base_al_validar_al_analista_no_da_503(seeded, monkeypatch):
    """Solo los errores de conexión, de tiempo o de SQLAlchemy dan 503; un defecto sale como 500."""

    async def broken_scope(claims, db):
        raise KeyError("partner_id")

    monkeypatch.setattr(operations_router, "operations_scope", broken_scope)
    transport = ASGITransport(app=app, raise_app_exceptions=False)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        response = await client.get(INDICATORS, headers=headers(ANALISTA))

    assert response.status_code == 500
    assert operations_router.DASHBOARD_UNAVAILABLE not in response.text
    assert "partner_id" not in response.text
