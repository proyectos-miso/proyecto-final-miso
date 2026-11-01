"""Tiempo de respuesta de la búsqueda y del detalle con 10.000 pólizas en PostgreSQL (HU-WEB-08: TC-08; HU-WEB-09: TC-09)."""
import random
import statistics
import time

import pytest
from httpx import AsyncClient
from sqlalchemy import text

from app.modules.policy.search import create_search_indexes

from tests.integration.policy_search_fixtures import (
    ALIADO,
    OTRO,
    add_policies,
    claim,
    headers,
    payment,
    policy_row,
    seed_partners_and_users,
)

pytestmark = pytest.mark.integration

URL = "/api/v1/experience/policies/search"
DETAIL_URL = "/api/v1/experience/policies/{}"
POLICIES = 10_000
SEARCHES = 200
DETAILS = 100
P95_LIMIT_MS = 150


async def test_tc_08_el_p95_de_la_busqueda_queda_por_debajo_de_150_ms(async_client: AsyncClient, postgres_session):
    await seed_partners_and_users(postgres_session)
    owners = [None, ALIADO, OTRO]
    rows = [
        policy_row(
            f"POL-2026-{i:05d}",
            f"{1_000_000_000 + i}",
            partner_id=owners[i % 3],
            holder=f"Titular {i:05d} Prueba" if i % 3 == 0 else None,
            age_days=i % 365,
        )
        for i in range(POLICIES)
    ]
    for start in range(0, POLICIES, 2_000):
        await add_policies(postgres_session, rows[start:start + 2_000])
    await create_search_indexes(await postgres_session.connection())
    await postgres_session.execute(text("ANALYZE policies"))
    await postgres_session.commit()

    rng = random.Random(7)
    queries = [
        rng.choice([
            f"{1_000_000_000 + rng.randrange(POLICIES)}",
            f"POL-2026-{rng.randrange(POLICIES):05d}",
            "Banco Aliado",
            f"Titular {rng.randrange(0, POLICIES, 3):05d}",
            "",
        ])
        for _ in range(SEARCHES)
    ]
    analyst = headers("operaciones@solventa.example")
    await async_client.get(URL, params={"q": "calentamiento"}, headers=analyst)

    timings = []
    for query in queries:
        start = time.perf_counter()
        response = await async_client.get(URL, params={"q": query}, headers=analyst)
        timings.append((time.perf_counter() - start) * 1000)
        assert response.status_code == 200

    p95 = statistics.quantiles(timings, n=100)[94]
    print(f"\nBúsqueda con {POLICIES} pólizas: p50 {statistics.median(timings):.1f} ms, p95 {p95:.1f} ms")
    assert p95 < P95_LIMIT_MS


async def test_tc_08_la_busqueda_por_titular_y_por_documento_usa_los_indices_de_trigramas(postgres_session):
    await seed_partners_and_users(postgres_session)
    rows = [
        policy_row(f"POL-2026-{i:05d}", f"{1_000_000_000 + i}", holder=f"Titular {i:05d} Prueba", age_days=i % 365)
        for i in range(POLICIES)
    ]
    for start in range(0, POLICIES, 2_000):
        await add_policies(postgres_session, rows[start:start + 2_000])
    await create_search_indexes(await postgres_session.connection())
    await postgres_session.execute(text("ANALYZE policies"))
    await postgres_session.commit()

    for column, pattern, index in [
        ("holder_name", "%titular 01234%", "ix_policies_holder_name_trgm"),
        ("insured_document", "1000001234%", "ix_policies_insured_document_trgm"),
    ]:
        operator = "ILIKE" if column == "holder_name" else "LIKE"
        plan = (await postgres_session.execute(
            text(f"EXPLAIN SELECT * FROM policies WHERE {column} {operator} :pattern"), {"pattern": pattern}
        )).scalars().all()
        assert any(index in line for line in plan), plan


async def test_tc_09_el_p95_del_detalle_queda_por_debajo_de_150_ms(async_client: AsyncClient, postgres_session):
    await seed_partners_and_users(postgres_session)
    owners = [None, ALIADO, OTRO]
    rows = [
        policy_row(f"POL-2026-{i:05d}", f"{1_000_000_000 + i}", partner_id=owners[i % 3],
                   holder=f"Titular {i:05d} Prueba" if i % 3 == 0 else None, age_days=i % 365)
        for i in range(POLICIES)
    ]
    for start in range(0, POLICIES, 2_000):
        await add_policies(postgres_session, rows[start:start + 2_000])
    for i in range(0, POLICIES, 10):
        number = f"POL-2026-{i:05d}"
        postgres_session.add_all([payment(number, i * 3 + k, minutes=k) for k in range(3)] + [claim(number, i)])
    await postgres_session.commit()
    await postgres_session.execute(text("ANALYZE"))
    await postgres_session.commit()

    rng = random.Random(9)
    numbers = [f"POL-2026-{rng.randrange(POLICIES):05d}" for _ in range(DETAILS)]
    analyst = headers("operaciones@solventa.example")
    await async_client.get(DETAIL_URL.format("POL-2026-00000"), headers=analyst)

    timings = []
    for number in numbers:
        start = time.perf_counter()
        response = await async_client.get(DETAIL_URL.format(number), headers=analyst)
        timings.append((time.perf_counter() - start) * 1000)
        assert response.status_code == 200

    p95 = statistics.quantiles(timings, n=100)[94]
    print(f"\nDetalle con {POLICIES} pólizas: p50 {statistics.median(timings):.1f} ms, p95 {p95:.1f} ms")
    assert p95 < P95_LIMIT_MS
