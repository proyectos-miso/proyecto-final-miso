"""Bandeja, expediente, dictamen, liquidación y pago de siniestros contra PostgreSQL y S3 emulado (HU-WEB-15)."""
import pytest
from httpx import AsyncClient
from sqlalchemy import select, text

from app.core.storage import StorageUnavailableError, get_document_storage
from app.main import app
from app.modules.claims.service import claim_service
from app.modules.payments.models import PayoutDB
from app.modules.policy.models import PolicyDB
from tests.integration.claims_fixtures import TWO_BENEFICIARIES, bucket, photo, report
from tests.integration.policy_search_fixtures import (
    ALIADO,
    OTRO,
    REVOCADO,
    add_policies,
    headers,
    policy_row,
    seed_partners_and_users,
)

pytestmark = pytest.mark.integration

ANALISTA = "operaciones@solventa.example"
DEL_ALIADO = "analista@bancoaliado.example"
CLAIMS = "/api/v1/experience/claims"


def with_beneficiaries(row, beneficiaries):
    quote, policy = row
    policy["beneficiaries"] = beneficiaries
    return quote, policy


@pytest.fixture
async def storage():
    s3 = await bucket()
    app.dependency_overrides[get_document_storage] = lambda: s3
    yield s3
    app.dependency_overrides.pop(get_document_storage, None)


@pytest.fixture
async def claims(postgres_session, storage):
    session = postgres_session
    await seed_partners_and_users(session)
    await claim_service.seed_experts(session)
    await add_policies(session, [
        with_beneficiaries(policy_row("POL-2026-00001", "1020304050", holder="Carolina Prueba Gómez", plan="premium"),
                           TWO_BENEFICIARIES),
        policy_row("POL-2026-00002", "7788990011", partner_id=ALIADO),
        policy_row("POL-2026-00003", "5566778899", partner_id=OTRO),
        policy_row("POL-2026-00004", "4455667788", status="ISSUED", holder="Andrés Ejemplo"),
        policy_row("POL-2026-00005", "3344556677", partner_id=REVOCADO),
    ])
    await report(session, "SIN-2026-00001", "POL-2026-00001", minutes=4, coverage_id="death",
                 description="Fallecimiento del titular en accidente de tránsito.",
                 photos=[await photo(storage, "SIN-2026-00001", 1),
                         await photo(storage, "SIN-2026-00001", 2, altered=True, mock=True)])
    await report(session, "SIN-2026-00002", "POL-2026-00002", minutes=3, coverage_id="disability")
    await report(session, "SIN-2026-00003", "POL-2026-00003", minutes=2)
    await report(session, "SIN-2026-00004", "POL-2026-00004", minutes=1, coverage_id="disability")
    return session


async def get(client, path="", user=ANALISTA, role="analyst", **params):
    return await client.get(CLAIMS + path, headers=headers(user, role), params=params)


async def post(client, path, body, user=ANALISTA):
    return await client.post(CLAIMS + path, headers=headers(user), json=body)


JUSTIFICATION = "Se verificó el certificado de defunción y la cobertura vigente al momento del hecho."


async def test_tc_01_la_bandeja_lista_los_siniestros_mas_recientes_con_sus_campos(async_client: AsyncClient, claims):
    body = (await get(async_client)).json()

    assert [c["claim_number"] for c in body["items"]] == [
        "SIN-2026-00001", "SIN-2026-00002", "SIN-2026-00003", "SIN-2026-00004"]
    assert set(body["items"][0]) == {"claim_number", "policy_number", "reported_at", "status", "origin", "assigned_to"}
    assert body["items"][0]["status"] == "REPORTED"
    filtered = (await get(async_client, status="REJECTED")).json()
    assert filtered["items"] == []


async def test_tc_02_el_expediente_trae_poliza_asegurado_descripcion_fotos_e_historial(async_client: AsyncClient, claims):
    body = (await get(async_client, "/SIN-2026-00001")).json()

    assert body["policy_number"] == "POL-2026-00001"
    assert body["plan"] == "premium"
    assert body["insured_name"] == "Carolina Prueba Gómez"
    assert body["insured_document"] == "******4050"
    assert body["coverage"]["coverage_id"] == "death"
    assert body["description"].startswith("Fallecimiento")
    assert [p["position"] for p in body["photos"]] == [1, 2]
    assert body["photos"][0]["latitude"] == 4.6097
    assert body["photos"][0]["url"].startswith("http")
    assert [e["kind"] for e in body["events"]] == ["REPORTED"]
    assert "1020304050" not in str(body)


async def test_tc_03_la_foto_que_no_coincide_con_su_huella_sale_alterada(async_client: AsyncClient, claims):
    photos = (await get(async_client, "/SIN-2026-00001")).json()["photos"]

    assert [p["integrity"] for p in photos] == ["INTACT", "ALTERED"]
    assert photos[1]["mock_location"] is True
    assert photos[1]["trusted_time"] is False


async def test_tc_03_sin_s3_las_fotos_quedan_sin_verificar(async_client: AsyncClient, claims):
    class Down:
        async def get_bytes(self, key):
            raise StorageUnavailableError("caído")

        async def view_url(self, key, content_type, expires_in):
            raise StorageUnavailableError("caído")

    app.dependency_overrides[get_document_storage] = lambda: Down()
    photos = (await get(async_client, "/SIN-2026-00001")).json()["photos"]

    assert {p["integrity"] for p in photos} == {"UNVERIFIED"}
    assert {p["url"] for p in photos} == {None}


async def test_tc_04_el_analista_toma_el_caso_y_asigna_un_perito(async_client: AsyncClient, claims):
    assert (await post(async_client, "/SIN-2026-00001/assign", {"expert_id": "PER-BOG-01"})).status_code == 204
    body = (await get(async_client, "/SIN-2026-00001")).json()

    assert body["status"] == "IN_REVIEW"
    assert body["assigned_to"] == ANALISTA
    assert body["expert"]["city"] == "Bogotá"
    assert [e["kind"] for e in body["events"]] == ["REPORTED", "ASSIGNED", "EXPERT_ASSIGNED"]
    missing = await post(async_client, "/SIN-2026-00001/assign", {"expert_id": "PER-NO-EXISTE"})
    assert missing.status_code == 422


async def test_tc_05_el_rechazo_exige_justificacion_y_no_se_puede_decidir_dos_veces(async_client: AsyncClient, claims):
    short = await post(async_client, "/SIN-2026-00002/decision", {"decision": "REJECT", "justification": "No aplica"})
    assert short.status_code == 422

    assert (await post(async_client, "/SIN-2026-00002/decision",
                       {"decision": "REJECT", "justification": JUSTIFICATION})).status_code == 204
    again = await post(async_client, "/SIN-2026-00002/decision",
                       {"decision": "APPROVE", "justification": JUSTIFICATION, "amount": 1000})
    body = (await get(async_client, "/SIN-2026-00002")).json()

    assert again.status_code == 409
    assert body["status"] == "REJECTED"
    rejected = body["events"][-1]
    assert (rejected["kind"], rejected["actor"], rejected["justification"]) == ("REJECTED", ANALISTA, JUSTIFICATION)


async def test_tc_06_la_liquidacion_no_supera_el_amparo(async_client: AsyncClient, claims):
    over = await post(async_client, "/SIN-2026-00001/decision",
                      {"decision": "APPROVE", "justification": JUSTIFICATION, "amount": 200_000_001})
    no_amount = await post(async_client, "/SIN-2026-00001/decision",
                           {"decision": "APPROVE", "justification": JUSTIFICATION})

    assert over.status_code == 422
    assert over.json()["detail"] == "El valor supera la suma asegurada del amparo."
    assert no_amount.status_code == 422


async def test_tc_07_liquidar_paga_a_los_beneficiarios_y_termina_la_poliza_de_muerte(
    async_client: AsyncClient, claims
):
    response = await post(async_client, "/SIN-2026-00001/decision",
                          {"decision": "APPROVE", "justification": JUSTIFICATION, "amount": 150_000_000})
    body = (await get(async_client, "/SIN-2026-00001")).json()
    policy = await claims.scalar(select(PolicyDB).where(PolicyDB.policy_number == "POL-2026-00001"))
    await claims.refresh(policy)

    assert response.status_code == 204
    assert body["status"] == "PAID"
    assert body["settled_amount"] == 150_000_000
    assert [(p["percentage"], p["amount"], p["status"]) for p in body["payouts"]] == [
        (60, 90_000_000, "APPROVED"), (40, 60_000_000, "APPROVED")]
    assert all(p["receipt_number"].startswith("REC-") for p in body["payouts"])
    assert body["payouts"][0]["beneficiary_document"] == "****0111"
    assert [e["kind"] for e in body["events"]][-2:] == ["SETTLED", "PAID"]
    assert policy.status == "TERMINATED"


async def test_tc_08_no_se_liquida_una_poliza_sin_pagar(async_client: AsyncClient, claims):
    response = await post(async_client, "/SIN-2026-00004/decision",
                          {"decision": "APPROVE", "justification": JUSTIFICATION, "amount": 1000})

    assert response.status_code == 422


async def test_tc_09_el_pago_rechazado_se_reintenta_sin_repetir_los_aprobados(async_client: AsyncClient, claims):
    await claims.execute(text(
        "UPDATE policies SET beneficiaries = :b WHERE policy_number = 'POL-2026-00001'"
    ), {"b": '[{"name": "Cuenta inactiva", "document_type": "CC", "document_number": "9999999999", '
             '"relationship": "OTRO", "percentage": 50}, {"name": "Lucía Prueba Gómez", "document_type": "CC", '
             '"document_number": "52000111", "relationship": "CONYUGE", "percentage": 50}]'})
    await claims.commit()

    await post(async_client, "/SIN-2026-00001/decision",
               {"decision": "APPROVE", "justification": JUSTIFICATION, "amount": 10_000_000})
    first = (await get(async_client, "/SIN-2026-00001")).json()
    retry = await post(async_client, "/SIN-2026-00001/payouts/retry", {})
    payouts = list(await claims.scalars(select(PayoutDB).where(PayoutDB.reference == "SIN-2026-00001")))

    assert first["status"] == "SETTLED"
    assert sorted(p["status"] for p in first["payouts"]) == ["APPROVED", "DECLINED"]
    assert retry.status_code == 204
    assert len(payouts) == 2
    assert len({p.receipt_number for p in payouts if p.receipt_number}) == 1


async def test_tc_10_el_siniestro_parametrico_que_cumple_la_regla_se_liquida_solo(claims):
    await report(claims, "SIN-2026-00010", "POL-2026-00001", origin="PARAMETRIC",
                 parametric_event={"type": "SISMO", "magnitude": 7.1, "city": "Bogotá"})
    await report(claims, "SIN-2026-00011", "POL-2026-00002", origin="PARAMETRIC",
                 parametric_event={"type": "SISMO", "magnitude": 5.0, "city": "Bogotá"})

    rows = dict((await claims.execute(text(
        "SELECT claim_number, status FROM claims WHERE claim_number IN ('SIN-2026-00010', 'SIN-2026-00011')"
    ))).all())
    actor = await claims.scalar(text(
        "SELECT actor FROM claim_events WHERE claim_number = 'SIN-2026-00010' AND kind = 'SETTLED'"
    ))
    assert rows == {"SIN-2026-00010": "PAID", "SIN-2026-00011": "REPORTED"}
    assert actor == "Regla paramétrica SISMO-6.5"


async def test_tc_11_el_usuario_de_un_socio_solo_ve_y_decide_los_de_su_socio(async_client: AsyncClient, claims):
    listed = (await get(async_client, user=DEL_ALIADO)).json()["items"]
    other = await get(async_client, "/SIN-2026-00003", user=DEL_ALIADO)
    decide = await post(async_client, "/SIN-2026-00003/decision",
                        {"decision": "REJECT", "justification": JUSTIFICATION}, user=DEL_ALIADO)
    missing = await get(async_client, "/SIN-2026-99999", user=DEL_ALIADO)

    assert [c["claim_number"] for c in listed] == ["SIN-2026-00002"]
    assert other.status_code == decide.status_code == missing.status_code == 404
    assert other.json() == missing.json()


async def test_tc_12_solo_el_rol_analyst_con_socio_habilitado(async_client: AsyncClient, claims):
    advisor = await get(async_client, user="asesor@solventa.example", role="advisor")
    anonymous = await async_client.get(CLAIMS)
    revoked = await get(async_client, user="analista@revocado.example")
    bad_number = await get(async_client, "/SIN-1")

    assert (advisor.status_code, anonymous.status_code, revoked.status_code, bad_number.status_code) == (
        403, 401, 403, 422)


async def test_tc_13_el_historial_no_se_puede_cambiar_ni_borrar_en_la_base(async_client: AsyncClient, claims):
    for statement in ("UPDATE claim_events SET justification = 'otra' WHERE claim_number = 'SIN-2026-00001'",
                      "DELETE FROM claim_events WHERE claim_number = 'SIN-2026-00001'"):
        with pytest.raises(Exception, match="no se puede modificar ni borrar"):
            await claims.execute(text(statement))
        await claims.rollback()
