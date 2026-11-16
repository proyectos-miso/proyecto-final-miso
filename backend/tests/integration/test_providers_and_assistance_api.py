"""Prestadores cercanos y solicitudes de asistencia del asegurado contra PostgreSQL (mejora FE-MOV-07)."""
import asyncio
import uuid

import pytest
from sqlalchemy import func, select, update

from app.core.idempotency import IdempotencyRecordDB
from app.core.outbox import OutboxMessageDB
from app.modules.assistance.models import AssistanceRequestDB, ProviderDB
from app.modules.assistance.service import assistance_service
from app.modules.identity.db_models import InsuredAccountDB
from app.modules.identity.service import insured_auth_service
from app.modules.profile.service import profile_service
from tests.conftest import INSURED_PASSWORD
from tests.integration.policy_search_fixtures import add_policies, headers, policy_row
from tests.integration.test_insured_policies_api import MARIA_DOCUMENT, OTHER_DOCUMENT, insured_headers

pytestmark = [pytest.mark.integration, pytest.mark.usefixtures("seeded_insured")]

PROVIDERS = "/api/v1/insured/providers"
ASSISTANCE = "/api/v1/insured/assistance"
OWN = "POL-2026-00001"
FOREIGN = "POL-2026-00002"
EXPIRED = "POL-2026-00003"
# Cerca del parque de la calle 93 en Bogotá; coordenadas de prueba, no de una persona.
NEAR_NORTH_BOGOTA = {"lat": 4.676, "lng": -74.048}


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


def body(**changes) -> dict:
    return {"policy_number": OWN, "service": "GRUA", **NEAR_NORTH_BOGOTA, "description": "El carro no enciende.", **changes}


def key() -> dict:
    return {"Idempotency-Key": str(uuid.uuid4())}


async def ask(client, payload: dict, idempotency: dict = None, auth: dict = None):
    return await client.post(ASSISTANCE, json=payload, headers={**(auth or insured_headers()), **(idempotency or key())})


async def count(session, model) -> int:
    return await session.scalar(select(func.count()).select_from(model))


async def test_con_ubicacion_ordena_los_prestadores_por_distancia(async_client, catalog):
    response = await async_client.get(PROVIDERS, params=NEAR_NORTH_BOGOTA, headers=insured_headers())

    assert response.status_code == 200
    providers = response.json()["providers"]
    distances = [p["distance_km"] for p in providers]
    assert providers[0]["provider_id"] == "PRV-BOG-01"
    assert distances == sorted(distances)
    assert len(providers) == 18
    assert response.headers["cache-control"] == "no-store"


async def test_sin_ubicacion_filtra_por_ciudad_y_no_da_distancias(async_client, catalog):
    response = await async_client.get(PROVIDERS, params={"city": "MEDELLIN"}, headers=insured_headers())

    providers = response.json()["providers"]
    assert {p["city_code"] for p in providers} == {"MEDELLIN"}
    assert len(providers) == 4
    assert all(p["distance_km"] is None for p in providers)
    assert [p["name"] for p in providers] == sorted(p["name"] for p in providers)


async def test_el_filtro_por_servicio_deja_solo_quienes_lo_ofrecen(async_client, catalog):
    response = await async_client.get(PROVIDERS, params={**NEAR_NORTH_BOGOTA, "service": "CERRAJERIA"}, headers=insured_headers())

    providers = response.json()["providers"]
    assert [p["provider_id"] for p in providers] == ["PRV-BOG-04", "PRV-MED-04", "PRV-BAQ-02"]
    assert all("CERRAJERIA" in p["services"] for p in providers)


async def test_un_prestador_inactivo_no_aparece(async_client, catalog):
    await catalog.execute(update(ProviderDB).where(ProviderDB.provider_id == "PRV-BOG-01").values(active=False))
    await catalog.commit()

    response = await async_client.get(PROVIDERS, params={"city": "BOGOTA"}, headers=insured_headers())

    assert "PRV-BOG-01" not in [p["provider_id"] for p in response.json()["providers"]]


@pytest.mark.parametrize("params", [{}, {"lat": 4.6}, {"city": "bogota"}, {"lat": 120, "lng": 0}, {"city": "BOGOTA", "service": "PINTURA"}],
                         ids=["nada", "solo-latitud", "ciudad-mal-formada", "latitud-fuera-de-rango", "servicio-desconocido"])
async def test_una_busqueda_sin_ubicacion_ni_ciudad_valida_responde_422(async_client, catalog, params):
    response = await async_client.get(PROVIDERS, params=params, headers=insured_headers())

    assert response.status_code == 422


async def test_buscar_prestadores_no_guarda_la_ubicacion(async_client, catalog):
    before = {m: await count(catalog, m) for m in (AssistanceRequestDB, OutboxMessageDB, IdempotencyRecordDB)}

    await async_client.get(PROVIDERS, params=NEAR_NORTH_BOGOTA, headers=insured_headers())

    assert {m: await count(catalog, m) for m in before} == before


async def test_el_catalogo_de_ciudades_esta_disponible_para_el_asegurado(async_client, catalog):
    response = await async_client.get("/api/v1/insured/catalogs/cities", headers=insured_headers())

    assert response.status_code == 200
    assert {"BOGOTA", "MEDELLIN", "CALI", "BARRANQUILLA", "BUCARAMANGA"} <= {c["code"] for c in response.json()}


@pytest.mark.parametrize("method,url", [("GET", PROVIDERS + "?city=BOGOTA"), ("GET", "/api/v1/insured/catalogs/cities"),
                                        ("POST", ASSISTANCE), ("GET", ASSISTANCE)], ids=["prestadores", "ciudades", "pedir", "listar"])
async def test_sin_sesion_responde_401_y_con_otro_rol_403(async_client, catalog, method, url):
    anonymous = await async_client.request(method, url, json=body(), headers=key())
    analyst = await async_client.request(method, url, json=body(), headers={**headers("operaciones@solventa.example"), **key()})

    assert anonymous.status_code == 401
    assert analyst.status_code == 403


async def test_la_cuenta_en_verificacion_no_ve_prestadores_ni_pide_asistencia(async_client, catalog):
    await catalog.execute(update(InsuredAccountDB).where(InsuredAccountDB.user_id == "ins-0001")
                          .values(verification_status="PENDING"))
    await catalog.commit()

    providers = await async_client.get(PROVIDERS, params={"city": "BOGOTA"}, headers=insured_headers())
    requested = await ask(async_client, body())

    assert (providers.status_code, requested.status_code) == (403, 403)
    assert await count(catalog, AssistanceRequestDB) == 0


async def test_pide_asistencia_con_el_prestador_mas_cercano_y_su_tiempo_estimado(async_client, catalog):
    response = await ask(async_client, body())

    assert response.status_code == 201
    receipt = response.json()
    assert receipt["provider"]["provider_id"] == "PRV-BOG-01"
    assert receipt["status"] == "ASSIGNED" and receipt["service"] == "GRUA"
    assert receipt["provider"]["distance_km"] == 1.2
    assert receipt["eta_minutes"] == 18
    stored = await catalog.scalar(select(AssistanceRequestDB))
    assert (stored.user_id, stored.policy_number, stored.provider_id) == ("ins-0001", OWN, "PRV-BOG-01")


async def test_sin_ubicacion_asigna_en_la_ciudad_con_45_minutos(async_client, catalog):
    response = await ask(async_client, body(lat=None, lng=None, city="CALI", service="ATENCION_MEDICA"))

    assert response.status_code == 201
    assert response.json()["provider"]["provider_id"] == "PRV-CAL-03"
    assert response.json()["eta_minutes"] == 45


async def test_la_solicitud_no_guarda_la_ubicacion_y_el_evento_solo_lleva_identificadores(async_client, catalog):
    response = await ask(async_client, body())

    event = await catalog.scalar(select(OutboxMessageDB).where(OutboxMessageDB.event_type == "AssistanceRequested"))
    assert event.aggregate_id == response.json()["request_id"]
    assert event.payload == {"request_id": response.json()["request_id"], "provider_id": "PRV-BOG-01", "service": "GRUA"}
    record = await catalog.scalar(select(IdempotencyRecordDB))
    assert record.partner_id == "insured:ins-0001"
    assert "lat" not in record.response_body and "4.676" not in str(record.response_body)


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


async def test_la_misma_llave_con_otro_cuerpo_responde_409(async_client, catalog):
    same = key()

    await ask(async_client, body(), same)
    other = await ask(async_client, body(description="Otra cosa."), same)

    assert other.status_code == 409
    assert await count(catalog, AssistanceRequestDB) == 1


async def test_la_llave_es_de_cada_asegurado(async_client, catalog):
    await insured_auth_service.register(catalog, user_id="ins-0002", email="otra.persona@example.com",
                                        full_name="Otra Persona", document_number=OTHER_DOCUMENT, phone="+573009998877",
                                        password=INSURED_PASSWORD)
    same = key()
    mine = await ask(async_client, body(), same)

    other = await ask(async_client, body(policy_number=FOREIGN), same, auth=insured_headers("ins-0002"))

    assert (mine.status_code, other.status_code) == (201, 201)
    assert other.json()["request_id"] != mine.json()["request_id"]
    assert await count(catalog, AssistanceRequestDB) == 2


async def test_dos_envios_simultaneos_con_la_misma_llave_crean_una_sola_solicitud(async_client, catalog):
    same = key()

    responses = await asyncio.gather(ask(async_client, body(), same), ask(async_client, body(), same))

    assert [r.status_code for r in responses] == [201, 201]
    assert responses[0].json() == responses[1].json()
    assert await count(catalog, AssistanceRequestDB) == 1


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


async def test_sin_prestador_que_ofrezca_el_servicio_responde_409(async_client, catalog):
    response = await ask(async_client, body(lat=None, lng=None, city="CARTAGENA"))

    assert response.status_code == 409
    assert "línea de Solventa" in response.json()["detail"]
    assert await count(catalog, AssistanceRequestDB) == 0
    assert await count(catalog, IdempotencyRecordDB) == 0


async def test_mas_de_cinco_solicitudes_en_una_hora_responde_429(async_client, catalog):
    for _ in range(5):
        assert (await ask(async_client, body())).status_code == 201

    response = await ask(async_client, body())

    assert response.status_code == 429
    assert response.headers["retry-after"] == "600"


async def test_lista_solo_las_solicitudes_del_asegurado(async_client, catalog):
    created = await ask(async_client, body())

    mine = await async_client.get(ASSISTANCE, headers=insured_headers())

    assert mine.status_code == 200
    assert [r["request_id"] for r in mine.json()["requests"]] == [created.json()["request_id"]]
    assert mine.json()["requests"][0]["eta_minutes"] == created.json()["eta_minutes"]
    assert mine.headers["cache-control"] == "no-store"
