"""Auditoría de suscripción desde el portal de Solventa (HOTFIX-auditoria-de-suscripcion, FE-WEB-08)."""
import asyncio
import copy
import hashlib
import json
from datetime import datetime, timedelta, timezone

import pytest
from httpx import AsyncClient
from sqlalchemy import func, select, text
from sqlalchemy.exc import DBAPIError

from app.core.security import create_access_token
from app.modules.profile.models import ConsentDB, ConsentRecord
from app.modules.profile.service import profile_service
from app.modules.rating import partners
from app.modules.rating import service as rating_module
from app.modules.rating.models import PlanTier, QuoteDB
from app.modules.rating.service import rating_service
from app.modules.underwriting import audit, rules
from app.modules.underwriting.models import UnderwritingRejectionDB
from tests.integration.policy_search_fixtures import headers, seed_partners_and_users

pytestmark = pytest.mark.integration

ANALISTA = "operaciones@solventa.example"
DEL_ALIADO = "analista@bancoaliado.example"
DECISIONS = "/api/v1/experience/decisions"
REVOKE = "/api/v1/experience/consents/revoke"
DOCUMENT = "1020304050"
OTHER_DOCUMENT = "52194832"
OTHER_CONSENT = "cst-test-other-0001"
SEEDED_CONSENT = "cst-test-default-001"
OLD = datetime(2026, 10, 1, 12, 0, tzinfo=timezone.utc)


@pytest.fixture
async def seeded(postgres_session):
    await seed_partners_and_users(postgres_session)
    return postgres_session


def advisor() -> dict:
    return {"Authorization": f"Bearer {create_access_token(subject='asesor@solventa.com', role='advisor')}"}


async def quote(client: AsyncClient, amount: float = 100_000_000, occupation_risk: int = 2, **extra) -> dict:
    body = {"document_type": "CC", "document_number": DOCUMENT, "birth_date": "1990-05-15", "insured_amount": amount,
            "occupation_risk": occupation_risk, **extra}
    response = await client.post("/api/v1/experience/quotes", headers=advisor(), json=body)
    assert response.status_code == 200, response.text
    return response.json()


async def replay(client: AsyncClient, decision_id: str, user: str = ANALISTA):
    return await client.post(f"{DECISIONS}/{decision_id}/replay", headers=headers(user))


async def add_old_decisions(session) -> None:
    """Una cotización y un rechazo guardados antes de la auditoría: sin versión de reglas ni entradas."""
    session.add(QuoteDB(quote_id="QUO-0000000000AA", document_number=DOCUMENT, status="active", score_applied=700.0,
                        tiers=[], created_at=OLD, expires_at=OLD + timedelta(days=15)))
    session.add(UnderwritingRejectionDB(rejection_id="UWR-0000000000AA", document_number=DOCUMENT, rule_id="UW-01",
                                        reason="Supera el cupo.", requested_amount=900_000_000,
                                        max_approvable_amount=700_000_000, created_at=OLD))
    await session.commit()


async def add_other_consent(session) -> None:
    """Consentimiento vigente de otro titular (ficticio)."""
    now = datetime.now(timezone.utc)
    session.add(ConsentDB(id=OTHER_CONSENT, document_type="CC", document_number=OTHER_DOCUMENT, channel="web",
                          purpose="Perfilamiento actuarial", scope="Scoring", validity_days=30, status="GRANTED",
                          consent_hash=hashlib.sha256(b"otro titular").hexdigest(), granted_at=now,
                          expires_at=now + timedelta(days=30)))
    await session.commit()


async def stored_quote(session, quote_id: str) -> QuoteDB:
    """Copia en memoria (sin guardar) de una cotización, para recalcularla con otras salidas guardadas."""
    row = await session.get(QuoteDB, quote_id)
    return QuoteDB(quote_id=row.quote_id, document_number=row.document_number, status=row.status,
                   score_applied=row.score_applied, birth_date=row.birth_date, created_at=row.created_at,
                   insured_amount=row.insured_amount, occupation_risk=row.occupation_risk,
                   rules_version=row.rules_version, tiers=copy.deepcopy(row.tiers))


def audit_events(caplog) -> list:
    return [json.loads(record.getMessage()) for record in caplog.records if record.name == "solventa.audit"]


async def test_el_detalle_de_una_cotizacion_trae_entradas_reglas_y_desglose_con_el_documento_enmascarado(
    async_client: AsyncClient, seeded
):
    created = await quote(async_client)

    response = await async_client.get(f"{DECISIONS}/{created['quote_id']}", headers=headers(ANALISTA))

    assert response.status_code == 200
    assert response.headers["cache-control"] == "no-store"
    body = response.json()
    assert body["kind"] == "quote"
    assert body["rules_version"] == rules.RULES_VERSION == "2026.11"
    assert body["consent_revoked"] is False
    assert body["replayable"] is True
    assert body["inputs"] == {
        "document_masked": "******4050",
        "birth_date": "1990-05-15",
        "insured_amount": 100_000_000,
        "occupation_risk": 2,
        "score": created["score_applied"],
    }
    assert body["outcome"]["status"] == "active"
    assert [tier["tier"] for tier in body["outcome"]["tiers"]] == ["basic", "standard", "premium"]
    standard = body["outcome"]["tiers"][1]
    assert standard["monthly_premium"] == created["tiers"][1]["monthly_premium"]
    assert [factor["code"] for factor in standard["breakdown"]] == ["base_rate", "age", "occupation", "payment_history", "plan"]
    assert DOCUMENT not in response.text


async def test_el_rechazo_no_guarda_el_puntaje_ni_la_fecha_de_nacimiento(async_client: AsyncClient, seeded):
    rejected = await quote(async_client, amount=900_000_000)

    row = await seeded.get(UnderwritingRejectionDB, rejected["quote_id"])
    stored = await seeded.scalar(text(
        "SELECT count(*) FROM information_schema.columns WHERE table_name = 'underwriting_rejections' "
        "AND column_name IN ('score', 'birth_date')"
    ))

    assert row.rules_version == "2026.11"
    assert {"score", "birth_date"}.isdisjoint(UnderwritingRejectionDB.__table__.columns.keys())
    assert stored == 0


async def test_el_detalle_de_un_rechazo_muestra_solo_lo_guardado_con_el_documento_enmascarado(
    async_client: AsyncClient, seeded
):
    rejected = await quote(async_client, amount=900_000_000)

    response = await async_client.get(f"{DECISIONS}/{rejected['quote_id']}", headers=headers(ANALISTA))

    body = response.json()
    assert response.status_code == 200
    assert body["kind"] == "rejection"
    assert body["rules_version"] == "2026.11"
    assert body["replayable"] is False
    assert body["created_at"]
    assert body["inputs"] == {"document_masked": "******4050", "insured_amount": 900_000_000}
    assert body["outcome"]["status"] == "review_required"
    assert body["outcome"]["rule_id"] == "UW-01"
    assert body["outcome"]["reason"]
    assert body["outcome"]["max_approvable_amount"] == 700_000_000
    assert body["outcome"]["tiers"] == []
    assert DOCUMENT not in response.text and "1990-05-15" not in response.text


async def test_con_el_consentimiento_revocado_un_rechazo_omite_la_regla_el_motivo_y_los_montos(
    async_client: AsyncClient, seeded
):
    rejected = await quote(async_client, amount=900_000_000, consent_id=SEEDED_CONSENT)
    await async_client.post(REVOKE, headers=headers(ANALISTA), json={"document_number": DOCUMENT})

    response = await async_client.get(f"{DECISIONS}/{rejected['quote_id']}", headers=headers(ANALISTA))

    assert response.status_code == 200
    body = response.json()
    assert body["kind"] == "rejection"
    assert body["consent_revoked"] is True
    assert body["replayable"] is False
    assert body["rules_version"] == "2026.11"
    assert body["created_at"]
    assert body["inputs"] == {"document_masked": None}
    assert body["outcome"]["status"] == "review_required"
    assert {"rule_id", "reason", "max_approvable_amount"}.isdisjoint(body["outcome"])
    row = await seeded.get(UnderwritingRejectionDB, rejected["quote_id"])
    for revealing in (row.rule_id, row.reason, str(int(row.max_approvable_amount)), "900000000", "4050"):
        assert revealing not in response.text


async def test_el_recalculo_coincide_en_cotizaciones_activas_y_degradadas(
    async_client: AsyncClient, seeded, monkeypatch
):
    active = await quote(async_client, occupation_risk=3)

    async def slow_score(request, delay=0.0):
        await asyncio.sleep(0.2)
        return 820

    monkeypatch.setattr(rating_service, "_score_provider", slow_score)
    monkeypatch.setattr(rating_service, "bureau_timeout", 0.02)
    degraded = await quote(async_client, occupation_risk=4)
    assert degraded["status"] == "degraded"

    for decision in (active, degraded):
        response = await replay(async_client, decision["quote_id"])
        assert response.status_code == 200
        assert response.json() == {
            "decision_id": decision["quote_id"],
            "matches": True,
            "differences": [],
            "rules_version_then": "2026.11",
            "rules_version_now": "2026.11",
        }


async def test_el_recalculo_detecta_un_multiplicador_o_un_cupo_distintos(async_client: AsyncClient, seeded, monkeypatch):
    created = await quote(async_client, occupation_risk=2)
    monkeypatch.setattr(rules, "RULES_VERSION", "2027.01")
    monkeypatch.setitem(rating_module.OCCUPATION_MULTIPLIERS, 2, 1.2)

    quote_replay = (await replay(async_client, created["quote_id"])).json()
    monkeypatch.setattr(rules, "SCORE_LIMITS", [(0, 50_000_000)])
    over_limit = (await replay(async_client, created["quote_id"])).json()

    assert quote_replay["matches"] is False
    assert quote_replay["rules_version_then"] == "2026.11"
    assert quote_replay["rules_version_now"] == "2027.01"
    fields = {difference["field"]: difference for difference in quote_replay["differences"]}
    assert fields["standard.occupation.multiplier"]["then"] == 1.1
    assert fields["standard.occupation.multiplier"]["now"] == 1.2
    assert {"basic.monthly_premium", "standard.monthly_premium", "premium.monthly_premium"} <= set(fields)
    assert over_limit["matches"] is False
    assert over_limit["differences"] == [{"field": "decision", "then": "approved", "now": "review_required:UW-01"}]


async def test_el_recalculo_de_un_rechazo_responde_409_porque_no_guarda_el_puntaje(
    async_client: AsyncClient, seeded, caplog
):
    caplog.set_level("INFO", logger="solventa.audit")
    rejected = await quote(async_client, amount=900_000_000)
    await add_old_decisions(seeded)

    for decision_id in (rejected["quote_id"], "UWR-0000000000AA"):
        response = await replay(async_client, decision_id)

        assert response.status_code == 409
        assert response.json()["detail"] == audit.REJECTION_NOT_REPLAYABLE == (
            "Los rechazos no guardan el puntaje del buró (HU-WEB-07), así que no se pueden recalcular."
        )
    assert "DECISION_REPLAYED" not in [event["event_code"] for event in audit_events(caplog)]


async def test_una_decision_anterior_a_la_auditoria_se_consulta_pero_no_se_recalcula(async_client: AsyncClient, seeded):
    await add_old_decisions(seeded)

    for decision_id in ("QUO-0000000000AA", "UWR-0000000000AA"):
        detail = (await async_client.get(f"{DECISIONS}/{decision_id}", headers=headers(ANALISTA))).json()

        assert detail["rules_version"] is None
        assert detail["replayable"] is False
    response = await replay(async_client, "QUO-0000000000AA")
    assert response.status_code == 409
    assert response.json()["detail"] == "Decisión anterior a la auditoría: no se puede recalcular."


async def test_solo_el_analista_de_solventa_audita_y_sin_sesion_responde_401(async_client: AsyncClient, seeded):
    created = await quote(async_client)
    calls = [
        ("get", f"{DECISIONS}/{created['quote_id']}", None),
        ("post", f"{DECISIONS}/{created['quote_id']}/replay", None),
        ("post", REVOKE, {"document_number": DOCUMENT}),
    ]
    for method, url, body in calls:
        from_partner = await async_client.request(method, url, headers=headers(DEL_ALIADO), json=body)
        from_advisor = await async_client.request(method, url, headers=advisor(), json=body)
        anonymous = await async_client.request(method, url, json=body)

        assert from_partner.status_code == 403, url
        assert from_advisor.status_code == 403, url
        assert anonymous.status_code == 401, url
    consent = await seeded.get(ConsentDB, SEEDED_CONSENT)
    await seeded.refresh(consent)
    assert consent.status == "GRANTED"


async def test_un_identificador_inexistente_da_404_y_uno_mal_formado_422(async_client: AsyncClient, seeded):
    for decision_id in ("QUO-FFFFFFFFFFFF", "UWR-FFFFFFFFFFFF"):
        assert (await async_client.get(f"{DECISIONS}/{decision_id}", headers=headers(ANALISTA))).status_code == 404
        assert (await replay(async_client, decision_id)).status_code == 404
    for decision_id in ("POL-2026-00001", "QUO-1234", "quo-ffffffffffff"):
        assert (await async_client.get(f"{DECISIONS}/{decision_id}", headers=headers(ANALISTA))).status_code == 422
    for document in ("10.203", "1" * 21, "<script>"):
        response = await async_client.post(REVOKE, headers=headers(ANALISTA), json={"document_number": document})
        assert response.status_code == 422


async def test_el_404_del_detalle_responde_sin_cache(async_client: AsyncClient, seeded):
    response = await async_client.get(f"{DECISIONS}/UWR-FFFFFFFFFFFF", headers=headers(ANALISTA))

    assert response.status_code == 404
    assert response.headers["cache-control"] == "no-store"


@pytest.mark.parametrize(
    "statement",
    ["UPDATE quotes SET status = 'degraded'", "DELETE FROM quotes"],
    ids=["update", "delete"],
)
async def test_la_base_impide_cambiar_o_borrar_una_cotizacion(async_client: AsyncClient, seeded, statement):
    created = await quote(async_client)

    with pytest.raises(DBAPIError, match="no se pueden modificar ni borrar"):
        await seeded.execute(text(statement))
    await seeded.rollback()

    row = await seeded.scalar(select(QuoteDB).where(QuoteDB.quote_id == created["quote_id"]))
    assert row.status == "active"


async def test_la_revocacion_oculta_las_entradas_personales_e_impide_cotizar_con_ese_consentimiento(
    async_client: AsyncClient, seeded
):
    created = await quote(async_client, consent_id=SEEDED_CONSENT)
    rejected = await quote(async_client, amount=900_000_000)

    response = await async_client.post(REVOKE, headers=headers(ANALISTA), json={"document_number": DOCUMENT})

    assert response.status_code == 200
    assert response.json() == {"document_masked": "******4050", "revoked": 1}
    for decision in (created, rejected):
        detail = await async_client.get(f"{DECISIONS}/{decision['quote_id']}", headers=headers(ANALISTA))
        body = detail.json()
        assert body["consent_revoked"] is True
        assert body["inputs"].get("document_masked") is None
        assert body["inputs"].get("birth_date") is None
        assert body["inputs"].get("score") is None
        assert "1990-05-15" not in detail.text and "*4050" not in detail.text and DOCUMENT not in detail.text
    blocked = await replay(async_client, created["quote_id"])
    assert blocked.status_code == 409
    assert blocked.json()["detail"] == audit.CONSENT_REVOKED_NOT_REPLAYABLE

    body = {"document_type": "CC", "document_number": DOCUMENT, "birth_date": "1990-05-15", "insured_amount": 100_000_000}
    with_id = await async_client.post("/api/v1/experience/quotes", headers=advisor(), json={**body, "consent_id": SEEDED_CONSENT})
    without_id = await async_client.post("/api/v1/experience/quotes", headers=advisor(), json=body)
    assert with_id.status_code == 422
    assert with_id.json()["detail"] == "El consentimiento informado del cliente fue revocado."
    assert without_id.status_code == 422
    again = await async_client.post(REVOKE, headers=headers(ANALISTA), json={"document_number": DOCUMENT})
    assert again.json() == {"document_masked": "******4050", "revoked": 0}


async def test_un_consentimiento_recien_otorgado_tampoco_sirve_desde_la_copia_en_memoria_tras_revocarlo(
    async_client: AsyncClient, seeded
):
    document = "52194832"
    otp = await async_client.post("/api/v1/experience/consent/otp-request", headers=advisor(),
                                  json={"document_number": document, "document_type": "CC", "phone": "+573000000000"})
    granted = await async_client.post("/api/v1/experience/consent", headers=advisor(), json={
        "document_number": document, "channel": "web", "otp_code": otp.json()["debug_code"], "terms_accepted": True,
        "purpose": "Perfilamiento actuarial", "scope": "Scoring", "validity_days": 30,
    })
    assert granted.status_code == 200

    await async_client.post(REVOKE, headers=headers(ANALISTA), json={"document_number": document})
    # Simula otra réplica que conserva la copia en memoria del consentimiento otorgado.
    profile_service._consents[document] = ConsentRecord(**granted.json())

    response = await async_client.post("/api/v1/experience/quotes", headers=advisor(), json={
        "document_type": "CC", "document_number": document, "birth_date": "1992-07-20", "insured_amount": 120_000_000,
    })
    assert response.status_code == 422


async def test_la_revocacion_y_la_consulta_dejan_un_evento_sin_datos_personales(async_client: AsyncClient, seeded, caplog):
    caplog.set_level("INFO", logger="solventa.audit")
    created = await quote(async_client)

    await async_client.get(f"{DECISIONS}/{created['quote_id']}", headers=headers(ANALISTA))
    await replay(async_client, created["quote_id"])
    await async_client.post(REVOKE, headers=headers(ANALISTA), json={"document_number": DOCUMENT})

    events = audit_events(caplog)
    assert [event["event_code"] for event in events] == ["DECISION_VIEWED", "DECISION_REPLAYED", "CONSENT_REVOKED_BY_OPERATOR"]
    assert all(event["operator_ref"] == partners.operator_ref(ANALISTA) for event in events)
    assert events[2]["consents_revoked"] == 1
    logged = " ".join(record.getMessage() for record in caplog.records)
    for personal in (DOCUMENT, "*4050", "1990-05-15", ANALISTA):
        assert personal not in logged


async def test_con_el_consentimiento_revocado_el_detalle_de_una_cotizacion_solo_trae_planes_y_coberturas_sin_montos_y_no_se_recalcula(
    async_client: AsyncClient, seeded, caplog
):
    caplog.set_level("INFO", logger="solventa.audit")
    created = await quote(async_client, consent_id=SEEDED_CONSENT)
    await async_client.post(REVOKE, headers=headers(ANALISTA), json={"document_number": DOCUMENT})

    detail = await async_client.get(f"{DECISIONS}/{created['quote_id']}", headers=headers(ANALISTA))
    blocked = await replay(async_client, created["quote_id"])

    assert detail.status_code == 200
    body = detail.json()
    assert set(body) == {"decision_id", "kind", "created_at", "rules_version", "consent_revoked", "replayable",
                         "inputs", "outcome"}
    assert body["consent_revoked"] is True and body["replayable"] is False
    assert body["inputs"] == {}
    assert set(body["outcome"]) == {"status", "degradation_reason", "tiers"}
    tiers = body["outcome"]["tiers"]
    assert [tier["tier"] for tier in tiers] == ["basic", "standard", "premium"]
    assert all(set(tier) == {"tier", "coverages"} for tier in tiers)
    assert tiers[1]["coverages"] == [
        {"id": coverage["id"], "name": coverage["name"]} for coverage in created["tiers"][1]["coverages"]
    ]
    serialized = json.dumps(body, ensure_ascii=False)
    for revealing in ("monthly_premium", "annual_premium", "limit", "deductible", "insured_amount", "occupation_risk",
                      "birth_date", "score", "document_masked", "años", "payment_history", "breakdown"):
        assert revealing not in serialized
    for amount in ("100000000", "100000000.0"):
        assert amount not in serialized
    assert blocked.status_code == 409
    assert blocked.headers["cache-control"] == "no-store"
    assert blocked.json()["detail"] == (
        "El consentimiento del titular fue revocado: no se recalcula con sus datos personales."
    )
    for revealing in ("años", "payment_history", "breakdown", "monthly_premium", "limit"):
        assert revealing not in blocked.text
    assert "DECISION_REPLAYED" not in [event["event_code"] for event in audit_events(caplog)]


async def test_el_recalculo_y_la_revocacion_responden_sin_cache(async_client: AsyncClient, seeded):
    created = await quote(async_client)

    replayed = await replay(async_client, created["quote_id"])
    revoked = await async_client.post(REVOKE, headers=headers(ANALISTA), json={"document_number": DOCUMENT})

    assert replayed.status_code == 200 and revoked.status_code == 200
    assert replayed.headers["cache-control"] == "no-store"
    assert revoked.headers["cache-control"] == "no-store"


async def test_el_asesor_con_el_consentimiento_de_otro_documento_recibe_422_sin_consultar_el_buro_ni_guardar(
    async_client: AsyncClient, seeded, monkeypatch
):
    await add_other_consent(seeded)
    calls = []

    async def counted_score(request, delay=0.0):
        calls.append(request.document_number)
        return 720

    monkeypatch.setattr(rating_service, "_score_provider", counted_score)
    body = {"document_type": "CC", "document_number": DOCUMENT, "birth_date": "1990-05-15",
            "insured_amount": 100_000_000, "occupation_risk": 2, "consent_id": OTHER_CONSENT}

    response = await async_client.post("/api/v1/experience/quotes", headers=advisor(), json=body)

    assert response.status_code == 422
    assert response.json()["detail"] == rating_module.CONSENT_NOT_VALID_FOR_CLIENT == "El consentimiento no es válido para este cliente."
    assert calls == []
    assert await seeded.scalar(select(func.count()).select_from(QuoteDB)) == 0
    assert await seeded.scalar(select(func.count()).select_from(UnderwritingRejectionDB)) == 0


async def test_el_asesor_con_un_consentimiento_inexistente_recibe_422_sin_consultar_el_buro_ni_guardar(
    async_client: AsyncClient, seeded, monkeypatch
):
    calls = []

    async def counted_score(request, delay=0.0):
        calls.append(request.document_number)
        return 720

    monkeypatch.setattr(rating_service, "_score_provider", counted_score)
    body = {"document_type": "CC", "document_number": DOCUMENT, "birth_date": "1990-05-15",
            "insured_amount": 100_000_000, "occupation_risk": 2, "consent_id": "cst-no-existe-0001"}

    response = await async_client.post("/api/v1/experience/quotes", headers=advisor(), json=body)

    assert response.status_code == 422
    assert response.json()["detail"] == rating_module.CONSENT_NOT_VALID_FOR_CLIENT
    assert calls == []
    assert await seeded.scalar(select(func.count()).select_from(QuoteDB)) == 0
    assert await seeded.scalar(select(func.count()).select_from(UnderwritingRejectionDB)) == 0


async def test_una_cotizacion_con_un_consentimiento_ajeno_oculta_los_datos_si_el_documento_solo_tiene_revocados(
    async_client: AsyncClient, seeded
):
    await add_other_consent(seeded)
    seeded.add(QuoteDB(quote_id="QUO-0000000000BB", document_number=DOCUMENT, status="active", score_applied=720.0,
                       tiers=[], created_at=OLD, expires_at=OLD + timedelta(days=15), consent_id=OTHER_CONSENT,
                       birth_date=datetime(1990, 5, 15, tzinfo=timezone.utc), insured_amount=100_000_000,
                       occupation_risk=2, rules_version="2026.11"))
    await seeded.commit()
    before = (await async_client.get(f"{DECISIONS}/QUO-0000000000BB", headers=headers(ANALISTA))).json()

    await async_client.post(REVOKE, headers=headers(ANALISTA), json={"document_number": DOCUMENT})
    after = await async_client.get(f"{DECISIONS}/QUO-0000000000BB", headers=headers(ANALISTA))

    assert before["consent_revoked"] is False
    assert after.json()["consent_revoked"] is True
    assert after.json()["inputs"] == {} and after.json()["replayable"] is False
    assert "1990-05-15" not in after.text and "*4050" not in after.text
    assert (await replay(async_client, "QUO-0000000000BB")).status_code == 409


async def test_el_recalculo_reporta_los_tiers_que_desaparecen_o_aparecen(async_client: AsyncClient, seeded, monkeypatch):
    created = await quote(async_client)
    monkeypatch.setattr(rating_module, "OFFERED_TIERS", [PlanTier.BASIC, PlanTier.STANDARD])

    gone = (await replay(async_client, created["quote_id"])).json()
    monkeypatch.setattr(rating_module, "OFFERED_TIERS", list(PlanTier))
    row = await stored_quote(seeded, created["quote_id"])
    row.tiers = [tier for tier in row.tiers if tier["tier"] != "basic"]
    appeared = audit._replay_quote(row)

    assert gone["matches"] is False
    assert gone["differences"] == [{"field": "premium.tier", "then": "premium", "now": None}]
    assert [difference.model_dump() for difference in appeared] == [{"field": "basic.tier", "then": None, "now": "basic"}]


async def test_el_recalculo_compara_la_prima_anual_y_las_coberturas(async_client: AsyncClient, seeded):
    created = await quote(async_client)
    row = await stored_quote(seeded, created["quote_id"])
    standard = next(tier for tier in row.tiers if tier["tier"] == "standard")
    standard["annual_premium"] += 1_000
    standard["coverages"][0]["limit"] = 50_000_000
    standard["coverages"] = [coverage for coverage in standard["coverages"] if coverage["id"] != "disability"]

    fields = {difference.field: difference for difference in audit._replay_quote(row)}

    assert set(fields) == {"standard.annual_premium", "standard.coverages.death.limit", "standard.coverages.disability"}
    assert fields["standard.annual_premium"].now == created["tiers"][1]["annual_premium"]
    assert fields["standard.coverages.death.limit"].then == 50_000_000
    assert fields["standard.coverages.death.limit"].now == 100_000_000
    assert (fields["standard.coverages.disability"].then, fields["standard.coverages.disability"].now) == (None, "disability")


async def test_el_recalculo_tolera_diferencias_de_coma_flotante_de_hasta_medio_centavo(async_client: AsyncClient, seeded):
    created = await quote(async_client)
    row = await stored_quote(seeded, created["quote_id"])
    basic = next(tier for tier in row.tiers if tier["tier"] == "basic")
    basic["monthly_premium"] += 0.004
    basic["annual_premium"] -= 0.004
    basic["breakdown"][1]["multiplier"] += 0.004
    tolerated = audit._replay_quote(row)
    basic["monthly_premium"] += 0.002

    differences = audit._replay_quote(row)

    assert tolerated == []
    assert [difference.field for difference in differences] == ["basic.monthly_premium"]
