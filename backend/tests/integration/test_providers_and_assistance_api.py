"""Prestadores cercanos y solicitudes de asistencia del asegurado contra PostgreSQL (mejora FE-MOV-07)."""
import asyncio
import uuid
from datetime import datetime, timedelta, timezone

import pytest
from sqlalchemy import func, select, update

from app.bff.insured import router as router_module
from app.core.idempotency import IdempotencyRecordDB, purge_expired, request_hash
from app.core.outbox import OutboxMessageDB
from app.modules.assistance.models import AssistanceRequest, AssistanceRequestDB, ProviderDB
from app.modules.assistance.service import assistance_service
from app.modules.identity.db_models import InsuredAccountDB
from app.modules.identity.service import insured_auth_service
from app.modules.profile.service import profile_service
from tests.conftest import INSURED_PASSWORD
from tests.integration.policy_search_fixtures import add_policies, headers, policy_row
from tests.integration.test_claim_report_api import report_token
from tests.integration.test_insured_policies_api import MARIA_DOCUMENT, OTHER_DOCUMENT, insured_headers

pytestmark = [pytest.mark.integration, pytest.mark.usefixtures("seeded_insured")]

SEARCH = "/api/v1/insured/providers/search"
ASSISTANCE = "/api/v1/insured/assistance"
CITIES = "/api/v1/insured/catalogs/cities"
OWN = "POL-2026-00001"
FOREIGN = "POL-2026-00002"
EXPIRED = "POL-2026-00003"
# Cerca del parque de la calle 93 en Bogotá; coordenadas de prueba, no de una persona.
NEAR_NORTH_BOGOTA = {"lat": 4.676, "lng": -74.048}
# Centro de Pasto: ninguna ciudad del catálogo ficticio está a menos de 50 km.
PASTO = {"lat": 1.21, "lng": -77.28}


@pytest.fixture
async def catalog(postgres_session):
    session = postgres_session
    await profile_service.seed_default_catalogs(session)
    await assistance_service.seed_providers(session)
    await add_policies(session, [
        policy_row(OWN, MARIA_DOCUMENT),
        policy_row(FOREIGN, OTHER_DOCUMENT),
        policy_row(EXPIRED, MARIA_DOCUMENT, age_days=400),
    ])
    return session


async def other_insured(session):
    await insured_auth_service.register(session, user_id="ins-0002", email="otra.persona@example.com",
                                        full_name="Otra Persona", document_number=OTHER_DOCUMENT, phone="+573009998877",
                                        password=INSURED_PASSWORD)


def body(**changes) -> dict:
    return {"policy_number": OWN, "service": "GRUA", **NEAR_NORTH_BOGOTA, "description": "El carro no enciende.", **changes}


def key() -> dict:
    return {"Idempotency-Key": str(uuid.uuid4())}


async def search(client, payload: dict, auth: dict = None):
    return await client.post(SEARCH, json=payload, headers=auth or insured_headers())


async def ask(client, payload: dict, idempotency: dict = None, auth: dict = None):
    return await client.post(ASSISTANCE, json=payload, headers={**(auth or insured_headers()), **(idempotency or key())})


async def count(session, model) -> int:
    return await session.scalar(select(func.count()).select_from(model))


def first_arrivals_wait_together(monkeypatch, target, name: str, arrivals: int):
    """Las primeras `arrivals` llamadas esperan a estar todas antes de seguir; las siguientes pasan directo."""
    original = getattr(target, name)
    arrived = asyncio.Event()
    calls = []

    async def together(*args, **kwargs):
        calls.append(name)
        if len(calls) <= arrivals:
            if len(calls) == arrivals:
                arrived.set()
            await asyncio.wait_for(arrived.wait(), timeout=10)
        return await original(*args, **kwargs)

    monkeypatch.setattr(target, name, together)
    return calls


async def test_con_ubicacion_ordena_los_prestadores_a_50_km_por_distancia(async_client, catalog):
    response = await search(async_client, NEAR_NORTH_BOGOTA)

    assert response.status_code == 200
    providers = response.json()["providers"]
    distances = [p["distance_km"] for p in providers]
    assert providers[0]["provider_id"] == "PRV-BOG-01"
    assert distances == sorted(distances) and max(distances) <= 50
    assert {p["city_code"] for p in providers} == {"BOGOTA"}
    assert len(providers) == 5
    assert response.headers["cache-control"] == "no-store"


async def test_lejos_de_todo_prestador_la_lista_sale_vacia(async_client, catalog):
    response = await search(async_client, PASTO)

    assert response.status_code == 200
    assert response.json()["providers"] == []


async def test_sin_ubicacion_filtra_por_ciudad_y_no_da_distancias(async_client, catalog):
    response = await search(async_client, {"city": "MEDELLIN"})

    providers = response.json()["providers"]
    assert {p["city_code"] for p in providers} == {"MEDELLIN"}
    assert len(providers) == 4
    assert all(p["distance_km"] is None for p in providers)
    assert [p["name"] for p in providers] == sorted(p["name"] for p in providers)


async def test_el_filtro_por_servicio_deja_solo_quienes_lo_ofrecen(async_client, catalog):
    near = await search(async_client, {**NEAR_NORTH_BOGOTA, "service": "CERRAJERIA"})
    by_city = await search(async_client, {"city": "MEDELLIN", "service": "CERRAJERIA"})

    assert [p["provider_id"] for p in near.json()["providers"]] == ["PRV-BOG-04"]
    assert [p["provider_id"] for p in by_city.json()["providers"]] == ["PRV-MED-04"]
    assert all("CERRAJERIA" in p["services"] for p in near.json()["providers"] + by_city.json()["providers"])


async def test_el_filtro_por_servicio_se_hace_en_la_consulta(catalog):
    found = await assistance_service.nearby(catalog, None, None, "BOGOTA", "PLOMERIA", limit=1)

    assert [p.provider_id for p, _ in found] == ["PRV-BOG-05"]


async def test_un_prestador_inactivo_no_aparece(async_client, catalog):
    await catalog.execute(update(ProviderDB).where(ProviderDB.provider_id == "PRV-BOG-01").values(active=False))
    await catalog.commit()

    response = await search(async_client, {"city": "BOGOTA"})

    assert "PRV-BOG-01" not in [p["provider_id"] for p in response.json()["providers"]]


@pytest.mark.parametrize("payload", [{}, {"lat": 4.6}, {"city": "bogota"}, {"lat": 120, "lng": 0},
                                     {"city": "BOGOTA", "service": "PINTURA"}, {"city": "BOGOTA", "extra": 1}],
                         ids=["nada", "solo-latitud", "ciudad-mal-formada", "latitud-fuera-de-rango",
                              "servicio-desconocido", "campo-extra"])
async def test_una_busqueda_sin_ubicacion_ni_ciudad_valida_responde_422(async_client, catalog, payload):
    response = await search(async_client, payload)

    assert response.status_code == 422


async def test_la_ubicacion_no_va_en_la_url_la_busqueda_por_get_ya_no_existe(async_client, catalog):
    response = await async_client.get("/api/v1/insured/providers", params=NEAR_NORTH_BOGOTA, headers=insured_headers())

    assert response.status_code in (404, 405)


async def test_buscar_prestadores_no_guarda_la_ubicacion(async_client, catalog):
    before = {m: await count(catalog, m) for m in (AssistanceRequestDB, OutboxMessageDB, IdempotencyRecordDB)}

    await search(async_client, NEAR_NORTH_BOGOTA)

    assert {m: await count(catalog, m) for m in before} == before


async def test_el_catalogo_de_ciudades_esta_disponible_para_el_asegurado(async_client, catalog):
    response = await async_client.get(CITIES, headers=insured_headers())

    assert response.status_code == 200
    assert {"BOGOTA", "MEDELLIN", "CALI", "BARRANQUILLA", "BUCARAMANGA"} <= {c["code"] for c in response.json()}


ROUTES = [("POST", SEARCH), ("GET", CITIES), ("POST", ASSISTANCE), ("GET", ASSISTANCE)]
ROUTE_IDS = ["prestadores", "ciudades", "pedir", "listar"]


@pytest.mark.parametrize("method,url", ROUTES, ids=ROUTE_IDS)
async def test_sin_sesion_responde_401_y_con_otro_rol_403(async_client, catalog, method, url):
    anonymous = await async_client.request(method, url, json=body(), headers=key())
    analyst = await async_client.request(method, url, json=body(), headers={**headers("operaciones@solventa.example"), **key()})

    assert anonymous.status_code == 401
    assert analyst.status_code == 403


@pytest.mark.parametrize("method,url", ROUTES, ids=ROUTE_IDS)
async def test_el_token_de_solo_reporte_responde_401(async_client, catalog, method, url):
    _, report_headers = await report_token(catalog)

    response = await async_client.request(method, url, json=body(), headers={**report_headers, **key()})

    assert response.status_code == 401
    assert await count(catalog, AssistanceRequestDB) == 0


async def test_la_cuenta_en_verificacion_no_ve_prestadores_ni_pide_asistencia(async_client, catalog):
    await catalog.execute(update(InsuredAccountDB).where(InsuredAccountDB.user_id == "ins-0001")
                          .values(verification_status="PENDING"))
    await catalog.commit()

    providers = await search(async_client, {"city": "BOGOTA"})
    requested = await ask(async_client, body())

    assert (providers.status_code, requested.status_code) == (403, 403)
    assert providers.json() == {"detail": "Estamos verificando su identidad.", "code": "IDENTITY_IN_REVIEW"}
    assert requested.json()["code"] == "IDENTITY_IN_REVIEW"
    assert await count(catalog, AssistanceRequestDB) == 0


async def test_recibe_la_solicitud_con_el_prestador_sugerido_y_su_tiempo_estimado(async_client, catalog):
    response = await ask(async_client, body())

    assert response.status_code == 201
    receipt = response.json()
    assert receipt["provider"]["provider_id"] == "PRV-BOG-01"
    assert receipt["status"] == "RECEIVED" and receipt["service"] == "GRUA"
    assert receipt["provider"]["distance_km"] == 1.2
    assert receipt["eta_minutes"] == 18
    stored = await catalog.scalar(select(AssistanceRequestDB))
    assert (stored.user_id, stored.policy_number, stored.provider_id, stored.status) == ("ins-0001", OWN, "PRV-BOG-01", "RECEIVED")


async def test_sin_ubicacion_sugiere_en_la_ciudad_con_45_minutos(async_client, catalog):
    response = await ask(async_client, body(lat=None, lng=None, city="CALI", service="ATENCION_MEDICA"))

    assert response.status_code == 201
    assert response.json()["provider"]["provider_id"] == "PRV-CAL-03"
    assert response.json()["eta_minutes"] == 45


async def test_la_solicitud_no_guarda_la_ubicacion_ni_en_la_huella_y_el_evento_solo_lleva_identificadores(async_client, catalog):
    payload = body()
    response = await ask(async_client, payload)

    event = await catalog.scalar(select(OutboxMessageDB).where(OutboxMessageDB.event_type == "AssistanceRequested"))
    assert event.aggregate_id == response.json()["request_id"]
    assert event.payload == {"request_id": response.json()["request_id"], "provider_id": "PRV-BOG-01", "service": "GRUA"}
    record = await catalog.scalar(select(IdempotencyRecordDB))
    assert record.partner_id == "insured:ins-0001"
    assert "lat" not in record.response_body and "4.676" not in str(record.response_body)
    request = AssistanceRequest.model_validate(payload)
    assert record.request_hash == request_hash(request, {"lat", "lng"})
    assert record.request_hash != request_hash(request)
    assert record.request_hash == request_hash(AssistanceRequest.model_validate(body(**PASTO)), {"lat", "lng"})


@pytest.mark.parametrize("policy", [FOREIGN, EXPIRED, "POL-2026-99999"], ids=["ajena", "vencida", "inexistente"])
async def test_una_poliza_ajena_o_vencida_responde_404_sin_registrar_nada(async_client, catalog, policy):
    response = await ask(async_client, body(policy_number=policy))

    assert response.status_code == 404
    assert await count(catalog, AssistanceRequestDB) == 0
    assert await count(catalog, IdempotencyRecordDB) == 0


async def test_la_misma_llave_con_el_mismo_cuerpo_devuelve_la_misma_respuesta(async_client, catalog):
    same = key()

    first = await ask(async_client, body(), same)
    again = await ask(async_client, body(), same)

    assert (first.status_code, again.status_code) == (201, 201)
    assert again.json() == first.json()
    assert await count(catalog, AssistanceRequestDB) == 1
    assert await count(catalog, OutboxMessageDB) == 1


async def test_la_misma_llave_con_otra_ubicacion_repite_la_respuesta_porque_la_huella_no_la_incluye(async_client, catalog):
    same = key()

    first = await ask(async_client, body(), same)
    moved = await ask(async_client, body(lat=4.68, lng=-74.05), same)

    assert moved.json() == first.json()
    assert await count(catalog, AssistanceRequestDB) == 1


async def test_la_misma_llave_con_otro_cuerpo_responde_409_con_su_codigo(async_client, catalog):
    same = key()

    await ask(async_client, body(), same)
    other = await ask(async_client, body(description="Otra cosa."), same)

    assert other.status_code == 409
    assert other.json()["code"] == "IDEMPOTENCY_KEY_REUSED"
    assert await count(catalog, AssistanceRequestDB) == 1


async def test_la_llave_es_de_cada_asegurado(async_client, catalog):
    await other_insured(catalog)
    same = key()
    mine = await ask(async_client, body(), same)

    other = await ask(async_client, body(policy_number=FOREIGN), same, auth=insured_headers("ins-0002"))

    assert (mine.status_code, other.status_code) == (201, 201)
    assert other.json()["request_id"] != mine.json()["request_id"]
    assert await count(catalog, AssistanceRequestDB) == 2


async def test_dos_envios_simultaneos_con_la_misma_llave_crean_una_sola_solicitud(async_client, catalog, monkeypatch):
    # Los dos pasan `find_response` sin respuesta guardada; el segundo choca con la llave primaria al confirmar.
    calls = first_arrivals_wait_together(monkeypatch, router_module, "find_response", arrivals=2)
    same = key()

    responses = await asyncio.gather(ask(async_client, body(), same), ask(async_client, body(), same))

    assert [r.status_code for r in responses] == [201, 201]
    assert responses[0].json() == responses[1].json()
    assert len(calls) == 3
    assert await count(catalog, AssistanceRequestDB) == 1
    assert await count(catalog, IdempotencyRecordDB) == 1


@pytest.mark.parametrize("idempotency", [{}, {"Idempotency-Key": "corta"}, {"Idempotency-Key": "con espacios; y otros"}],
                         ids=["sin-llave", "corta", "caracteres"])
async def test_sin_llave_valida_responde_422(async_client, catalog, idempotency):
    response = await async_client.post(ASSISTANCE, json=body(), headers={**insured_headers(), **idempotency})

    assert response.status_code == 422
    assert await count(catalog, AssistanceRequestDB) == 0


@pytest.mark.parametrize("changes", [{"lat": None}, {"lat": None, "lng": None}, {"service": "PINTURA"},
                                     {"description": "x" * 301}, {"extra": 1}, {"policy_number": "123"}],
                         ids=["solo-longitud", "sin-ubicacion-ni-ciudad", "servicio", "descripcion-larga", "campo-extra", "poliza"])
async def test_un_cuerpo_invalido_responde_422(async_client, catalog, changes):
    response = await ask(async_client, body(**changes))

    assert response.status_code == 422


async def test_sin_prestador_que_ofrezca_el_servicio_responde_409_con_su_codigo(async_client, catalog):
    response = await ask(async_client, body(lat=None, lng=None, city="CARTAGENA"))

    assert response.status_code == 409
    assert response.json()["code"] == "NO_PROVIDER"
    assert "línea de Solventa" not in response.json()["detail"]
    assert await count(catalog, AssistanceRequestDB) == 0
    assert await count(catalog, IdempotencyRecordDB) == 0


async def test_sin_prestador_del_servicio_a_50_km_responde_409(async_client, catalog):
    response = await ask(async_client, body(service="PLOMERIA", **PASTO))

    assert response.status_code == 409
    assert response.json()["code"] == "NO_PROVIDER"
    assert await count(catalog, AssistanceRequestDB) == 0


async def test_mas_de_cinco_solicitudes_en_una_hora_responde_429_con_el_tiempo_que_falta(async_client, catalog):
    for _ in range(5):
        assert (await ask(async_client, body())).status_code == 201
    # La más antigua de las cinco sale de la ventana en unos 10 minutos.
    await catalog.execute(update(AssistanceRequestDB).values(
        created_at=datetime.now(timezone.utc) - timedelta(minutes=50)
    ))
    await catalog.commit()

    response = await ask(async_client, body())

    assert response.status_code == 429
    assert response.json()["code"] == "TOO_MANY_REQUESTS"
    assert "línea de Solventa" not in response.json()["detail"]
    assert 580 <= int(response.headers["retry-after"]) <= 600


async def test_el_tope_por_hora_se_respeta_con_solicitudes_simultaneas(async_client, catalog, monkeypatch):
    for _ in range(4):
        assert (await ask(async_client, body())).status_code == 201
    # Las tres llegan juntas al bloqueo de la cuenta; sin él, las tres contarían cuatro y pasarían.
    first_arrivals_wait_together(monkeypatch, assistance_service, "lock_account", arrivals=3)

    responses = await asyncio.gather(*(ask(async_client, body()) for _ in range(3)))

    assert sorted(r.status_code for r in responses) == [201, 429, 429]
    assert await count(catalog, AssistanceRequestDB) == 5


async def test_lista_solo_las_solicitudes_del_asegurado(async_client, catalog):
    await other_insured(catalog)
    created = await ask(async_client, body())
    foreign = await ask(async_client, body(policy_number=FOREIGN), auth=insured_headers("ins-0002"))

    mine = await async_client.get(ASSISTANCE, headers=insured_headers())

    assert foreign.status_code == 201
    assert mine.status_code == 200
    assert [r["request_id"] for r in mine.json()["requests"]] == [created.json()["request_id"]]
    assert foreign.json()["request_id"] not in str(mine.json())
    assert mine.json()["requests"][0]["eta_minutes"] == created.json()["eta_minutes"]
    assert mine.headers["cache-control"] == "no-store"


async def test_la_purga_borra_las_llaves_de_mas_de_24_horas(catalog):
    now = datetime.now(timezone.utc)
    catalog.add_all([
        IdempotencyRecordDB(partner_id="insured:ins-0001", idempotency_key="vieja-0001", request_hash="a" * 64,
                            response_status=201, response_body={}, created_at=now - timedelta(hours=25)),
        IdempotencyRecordDB(partner_id="insured:ins-0001", idempotency_key="nueva-0001", request_hash="b" * 64,
                            response_status=201, response_body={}, created_at=now - timedelta(hours=23)),
    ])
    await catalog.commit()

    removed = await purge_expired(catalog)

    assert removed == 1
    assert list(await catalog.scalars(select(IdempotencyRecordDB.idempotency_key))) == ["nueva-0001"]
