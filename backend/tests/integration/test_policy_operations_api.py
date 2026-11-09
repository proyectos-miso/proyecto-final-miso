"""Beneficiarios, endosos, cancelación, auditoría, reportes y cierre de sesión del portal (HU-WEB-15)."""
import pytest
from httpx import AsyncClient
from sqlalchemy import select, text

from app.core.storage import get_document_storage
from app.main import app
from app.modules.auth.service import auth_service
from app.modules.payments.models import PaymentTransactionDB, PayoutDB
from app.modules.policy.models import PolicyDB
from tests.integration.claims_fixtures import TWO_BENEFICIARIES, bucket, report
from tests.integration.policy_search_fixtures import (
    ALIADO,
    NOW,
    add_policies,
    headers,
    payment,
    policy_row,
    seed_partners_and_users,
)
from tests.pdf_text import pdf_text

pytestmark = pytest.mark.integration

ANALISTA = "operaciones@solventa.example"
DEL_ALIADO = "analista@bancoaliado.example"
POLICIES = "/api/v1/experience/policies/{}"
JUSTIFICATION = "El cliente pidió el cambio por escrito y adjuntó los documentos de soporte."


@pytest.fixture
async def storage():
    s3 = await bucket()
    app.dependency_overrides[get_document_storage] = lambda: s3
    yield s3
    app.dependency_overrides.pop(get_document_storage, None)


@pytest.fixture
async def catalog(postgres_session, storage):
    session = postgres_session
    await seed_partners_and_users(session)
    quote, policy = policy_row("POL-2026-00001", "1020304050", holder="Carolina Prueba Gómez", age_days=10)
    policy["beneficiaries"] = TWO_BENEFICIARIES
    await add_policies(session, [
        (quote, policy),
        policy_row("POL-2026-00002", "7788990011", partner_id=ALIADO),
        policy_row("POL-2026-00003", "4455667788", status="ISSUED", holder="Andrés Ejemplo"),
    ])
    session.add(payment("POL-2026-00001", 1))
    await session.commit()
    return session


async def post(client, policy_number, path, body, user=ANALISTA):
    return await client.post(POLICIES.format(policy_number) + path, headers=headers(user), json=body)


async def detail(client, policy_number, user=ANALISTA):
    return (await client.get(POLICIES.format(policy_number), headers=headers(user))).json()


async def test_tc_14_el_detalle_muestra_los_beneficiarios_con_el_documento_enmascarado(async_client: AsyncClient, catalog):
    body = await detail(async_client, "POL-2026-00001")

    assert [(b["name"], b["relationship"], b["percentage"], b["document"]) for b in body["beneficiaries"]] == [
        ("Lucía Prueba Gómez", "CONYUGE", 60, "****0111"), ("Mateo Prueba Gómez", "HIJO", 40, "******0033")]
    assert "52000111" not in str(body)
    assert body["version"] == 1


@pytest.mark.parametrize(
    "beneficiaries",
    [
        [],
        [{**TWO_BENEFICIARIES[0], "percentage": 50}],
        [TWO_BENEFICIARIES[0], {**TWO_BENEFICIARIES[1], "document_number": "52000111"}],
        [{**TWO_BENEFICIARIES[0], "document_number": "12ab"}],
    ],
    ids=["sin-beneficiarios", "no-suman-100", "repetido", "documento-invalido"],
)
async def test_tc_15_la_emision_exige_beneficiarios_validos(async_client: AsyncClient, catalog, beneficiaries):
    response = await async_client.post(
        "/api/v1/experience/policies", headers=headers("asesor@solventa.example", "advisor"),
        json={"quote_id": "Q-NO-IMPORTA", "tier": "standard", "beneficiaries": beneficiaries},
    )

    assert response.status_code == 422


async def test_tc_16_el_endoso_de_beneficiarios_sube_la_version_y_emite_otra_caratula(
    async_client: AsyncClient, catalog, storage
):
    first_link = (await async_client.get(POLICIES.format("POL-2026-00001") + "/caratula",
                                         headers=headers(ANALISTA))).json()
    new = [{**TWO_BENEFICIARIES[0], "percentage": 100}]
    response = await post(async_client, "POL-2026-00001", "/endorsements",
                          {"kind": "BENEFICIARIES", "justification": JUSTIFICATION, "beneficiaries": new})
    second_link = (await async_client.get(POLICIES.format("POL-2026-00001") + "/caratula",
                                          headers=headers(ANALISTA))).json()
    key = await catalog.scalar(text("SELECT pdf_s3_key FROM policies WHERE policy_number = 'POL-2026-00001'"))
    pdf = await storage.get_bytes(key)

    assert response.status_code == 200
    assert response.json()["version"] == 2
    assert key == "caratulas/POL-2026-00001-v2.pdf"
    assert first_link["url"] != second_link["url"]
    assert "Versión 2" in pdf_text(pdf)
    assert "Lucía Prueba Gómez" not in pdf_text(pdf)
    assert [b["percentage"] for b in (await detail(async_client, "POL-2026-00001"))["beneficiaries"]] == [100]


async def test_tc_17_el_endoso_de_suma_solo_la_baja(async_client: AsyncClient, catalog):
    higher = await post(async_client, "POL-2026-00001", "/endorsements",
                        {"kind": "SUM_INSURED", "justification": JUSTIFICATION, "insured_amount": 300_000_000})
    lower = await post(async_client, "POL-2026-00001", "/endorsements",
                       {"kind": "SUM_INSURED", "justification": JUSTIFICATION, "insured_amount": 100_000_000})
    body = await detail(async_client, "POL-2026-00001")

    assert higher.status_code == 422
    assert lower.status_code == 200
    assert body["insured_amount"] == 100_000_000
    assert [c["limit"] for c in body["coverages"]] == [100_000_000, 100_000_000]
    assert body["monthly_premium"] == 100_000


async def test_tc_18_cancelar_devuelve_la_prima_proporcional_y_cierra_la_poliza(async_client: AsyncClient, catalog):
    response = await post(async_client, "POL-2026-00001", "/cancellation", {"reason": JUSTIFICATION})
    again = await post(async_client, "POL-2026-00001", "/cancellation", {"reason": JUSTIFICATION})
    endorse = await post(async_client, "POL-2026-00001", "/endorsements",
                         {"kind": "SUM_INSURED", "justification": JUSTIFICATION, "insured_amount": 1000})
    refund = await catalog.scalar(select(PayoutDB).where(PayoutDB.reference == "POL-2026-00001"))
    unpaid = await post(async_client, "POL-2026-00003", "/cancellation", {"reason": JUSTIFICATION})

    assert response.status_code == 200
    assert response.json()["status"] == "CANCELLED"
    assert 0 < response.json()["refund_amount"] <= 100_000
    assert (refund.kind, refund.status, refund.amount) == ("REFUND", "APPROVED", response.json()["refund_amount"])
    assert again.status_code == endorse.status_code == 409
    assert unpaid.json()["refund_amount"] == 0


async def test_tc_19_la_auditoria_reconstruye_las_decisiones_en_orden(async_client: AsyncClient, catalog):
    await post(async_client, "POL-2026-00001", "/endorsements",
               {"kind": "SUM_INSURED", "justification": JUSTIFICATION, "insured_amount": 150_000_000})
    await report(catalog, "SIN-2026-00001", "POL-2026-00001", minutes=60 * 24 * 30, coverage_id="disability")
    await post(async_client, "POL-2026-00001", "/cancellation", {"reason": JUSTIFICATION})
    body = (await async_client.get(POLICIES.format("POL-2026-00001") + "/audit", headers=headers(ANALISTA))).json()

    kinds = [e["kind"] for e in body["entries"]]
    assert kinds[0] == "ISSUED"
    assert {"PREMIUM_APPROVED", "ENDORSEMENT_SUM_INSURED", "CANCELLED", "CLAIM_REPORTED", "PAYOUT_APPROVED"} <= set(kinds)
    assert [e["occurred_at"] for e in body["entries"]] == sorted(e["occurred_at"] for e in body["entries"])
    endorsement = next(e for e in body["entries"] if e["kind"] == "ENDORSEMENT_SUM_INSURED")
    assert (endorsement["actor"], endorsement["justification"]) == (ANALISTA, JUSTIFICATION)


async def test_tc_20_las_operaciones_de_la_poliza_no_se_cambian_en_la_base(async_client: AsyncClient, catalog):
    await post(async_client, "POL-2026-00001", "/cancellation", {"reason": JUSTIFICATION})
    for statement in ("UPDATE policy_events SET justification = 'otra'", "DELETE FROM policy_events"):
        with pytest.raises(Exception, match="no se pueden modificar ni borrar"):
            await catalog.execute(text(statement))
        await catalog.rollback()


async def test_tc_21_el_usuario_de_un_socio_no_opera_polizas_de_otros_ni_descarga_reportes(
    async_client: AsyncClient, catalog
):
    other = await post(async_client, "POL-2026-00001", "/cancellation", {"reason": JUSTIFICATION}, user=DEL_ALIADO)
    audit = await async_client.get(POLICIES.format("POL-2026-00001") + "/audit", headers=headers(DEL_ALIADO))
    own = await async_client.get(POLICIES.format("POL-2026-00002") + "/audit", headers=headers(DEL_ALIADO))
    report_ = await async_client.get("/api/v1/experience/reports/2026-10", headers=headers(DEL_ALIADO))
    advisor = await async_client.post(POLICIES.format("POL-2026-00001") + "/cancellation",
                                      headers=headers("asesor@solventa.example", "advisor"), json={"reason": JUSTIFICATION})

    assert other.status_code == audit.status_code == 404
    assert own.status_code == 200
    assert report_.status_code == 403
    assert advisor.status_code == 403


async def test_tc_22_el_reporte_mensual_sale_en_csv_y_pdf_sin_datos_personales(async_client: AsyncClient, catalog):
    await post(async_client, "POL-2026-00001", "/cancellation", {"reason": JUSTIFICATION})
    period = NOW.strftime("%Y-%m")
    csv_response = await async_client.get(f"/api/v1/experience/reports/{period}", headers=headers(ANALISTA))
    pdf_response = await async_client.get(f"/api/v1/experience/reports/{period}", headers=headers(ANALISTA),
                                          params={"format": "pdf"})
    invalid = await async_client.get("/api/v1/experience/reports/2026-13", headers=headers(ANALISTA))

    rows = csv_response.content.decode("utf-8-sig").splitlines()
    assert csv_response.headers["content-disposition"] == f'attachment; filename="reporte-{period}.csv"'
    assert rows[0] == "periodo,destinatario,indicador,valor"
    assert f"{period},Regulador,Pólizas emitidas,3" in rows
    assert f"{period},Reasegurador,Porcentaje cedido (cuota parte),40" in rows
    assert pdf_response.content.startswith(b"%PDF")
    assert "1020304050" not in csv_response.text and "Carolina" not in pdf_text(pdf_response.content)
    assert invalid.status_code == 422


async def test_tc_23_cerrar_sesion_revoca_el_refresh_y_un_refresh_usado_no_sirve(async_client: AsyncClient, catalog):
    user = await auth_service.get_user_by_username(catalog, ANALISTA)
    tokens = auth_service.generate_session_tokens(user)
    refresh = "/api/v1/experience/auth/refresh"

    renewed = await async_client.post(refresh, json={"refresh_token": tokens.refresh_token})
    reused = await async_client.post(refresh, json={"refresh_token": tokens.refresh_token})
    logout = await async_client.post("/api/v1/experience/auth/logout",
                                     json={"refresh_token": renewed.json()["refresh_token"]})
    after = await async_client.post(refresh, json={"refresh_token": renewed.json()["refresh_token"]})
    junk = await async_client.post("/api/v1/experience/auth/logout", json={"refresh_token": "no-es-un-token"})

    assert renewed.status_code == 200
    assert reused.status_code == 401
    assert logout.status_code == junk.status_code == 204
    assert after.status_code == 401
