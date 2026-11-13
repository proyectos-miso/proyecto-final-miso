"""Push, línea de tiempo del asegurado y tarea diaria de pólizas (HU-MOV-11, SR-04, SR-05 y SR-06)."""
import asyncio
from datetime import datetime, timedelta, timezone
from types import SimpleNamespace

import pytest

import app.main as main
from app.core.config import settings
from app.modules.claims.insured import timeline
from app.modules.notifications import service

NOW = datetime(2026, 11, 1, 12, 0, tzinfo=timezone.utc)


@pytest.fixture(autouse=True)
def fresh_sender(monkeypatch):
    monkeypatch.setattr(settings, "FIREBASE_CREDENTIALS_FILE", None)
    monkeypatch.setattr(settings, "FIREBASE_CREDENTIALS_JSON", None)
    service.get_sender.cache_clear()
    yield
    service.get_sender.cache_clear()


def test_sin_credenciales_no_hay_push():
    assert service.get_sender() is None


def test_credencial_json_mal_formada_desactiva_el_push_sin_fallar(monkeypatch):
    monkeypatch.setattr(settings, "FIREBASE_CREDENTIALS_JSON", "{no es json")

    assert service.get_sender() is None


def test_la_credencial_json_llega_a_firebase_como_diccionario(monkeypatch):
    received = []
    monkeypatch.setattr(settings, "FIREBASE_CREDENTIALS_JSON", '{"type": "service_account"}')
    monkeypatch.setattr(service, "FirebaseSender", lambda source: received.append(source) or "emisor")

    assert service.get_sender() == "emisor"
    assert received == [{"type": "service_account"}]


def test_el_emisor_de_firebase_manda_el_texto_general_con_prioridad_alta(monkeypatch):
    import firebase_admin
    from firebase_admin import credentials, messaging

    sent = []
    monkeypatch.setattr(credentials, "Certificate", lambda source: ("certificado", source))
    monkeypatch.setattr(firebase_admin, "initialize_app", lambda cert, name: SimpleNamespace(cert=cert, name=name))
    monkeypatch.setattr(messaging, "send_each_for_multicast", lambda message, app: sent.append((message, app)))

    sender = service.FirebaseSender({"type": "service_account"})
    sender.send(["token-a"], "Solventa", "Su siniestro fue aprobado.", {"type": "CLAIM_STATUS", "claim_number": "SIN-1"})

    message, app = sent[0]
    assert app.name == "solventa"
    assert message.tokens == ["token-a"]
    assert message.data == {"type": "CLAIM_STATUS", "claim_number": "SIN-1", "title": "Solventa", "body": "Su siniestro fue aprobado."}
    assert message.android.priority == "high"


def event(kind, hours):
    return SimpleNamespace(kind=kind, created_at=NOW + timedelta(hours=hours))


def test_la_linea_de_tiempo_omite_eventos_internos_y_une_las_evaluaciones_seguidas():
    claim = SimpleNamespace(origin="APP")
    events = [event("REPORTED", 0), event("PHOTO_VERIFIED", 1), event("ASSIGNED", 2), event("ASSIGNED", 3),
              event("EXPERT_ASSIGNED", 4), event("SETTLED", 5)]

    steps = timeline(claim, events)

    assert [s.step for s in steps] == ["RECEIVED", "IN_REVIEW", "IN_REVIEW", "APPROVED"]
    assert steps[1].at == NOW + timedelta(hours=2)
    assert "perito" in steps[2].explanation


async def test_la_tarea_diaria_vence_y_avisa_con_una_sesion(monkeypatch):
    calls = []

    class Session:
        async def __aenter__(self):
            return "sesion"

        async def __aexit__(self, *args):
            return False

    async def expire_due(session):
        calls.append(("vencer", session))

    async def remind_renewals(session, notify):
        calls.append(("avisar", session, notify))

    monkeypatch.setattr(main, "AsyncSessionLocal", Session)
    monkeypatch.setattr(main.policy_operations, "expire_due", expire_due)
    monkeypatch.setattr(main.policy_operations, "remind_renewals", remind_renewals)

    await main.run_policy_lifecycle()

    assert calls == [("vencer", "sesion"), ("avisar", "sesion", main.notification_service.renewal_due)]


async def test_una_falla_de_la_tarea_diaria_no_la_detiene(monkeypatch, capsys):
    async def failing():
        raise RuntimeError("base caída")

    async def stop(seconds):
        assert seconds == 24 * 3600
        raise asyncio.CancelledError

    monkeypatch.setattr(main, "run_policy_lifecycle", failing)
    monkeypatch.setattr(main.asyncio, "sleep", stop)

    with pytest.raises(asyncio.CancelledError):
        await main.daily_policy_lifecycle()

    assert "no se pudo revisar el vencimiento" in capsys.readouterr().out
