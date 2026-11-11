"""Inicio de Firebase para los push (HU-MOV-11, SR-05 y SR-06)."""
import pytest

from app.core.config import settings
from app.modules.notifications import service


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
