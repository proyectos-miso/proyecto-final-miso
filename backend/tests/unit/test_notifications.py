"""Push, línea de tiempo del asegurado y tarea diaria de pólizas (HU-MOV-11, SR-04, SR-05 y SR-06)."""
import asyncio
import threading
import time
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


def test_dos_hilos_a_la_vez_inician_firebase_una_sola_vez(monkeypatch):
    started = []
    barrier = threading.Barrier(2)
    monkeypatch.setattr(settings, "FIREBASE_CREDENTIALS_JSON", '{"type": "service_account"}')

    def slow_sender(source):
        started.append(source)
        time.sleep(0.05)
        return object()

    monkeypatch.setattr(service, "FirebaseSender", slow_sender)
    results = []

    def call():
        barrier.wait()
        results.append(service.get_sender())

    threads = [threading.Thread(target=call) for _ in range(2)]
    for thread in threads:
        thread.start()
    for thread in threads:
        thread.join()

    assert len(started) == 1
    assert results[0] is not None and results[0] is results[1]


def test_el_emisor_de_firebase_manda_el_texto_general_con_prioridad_alta_y_un_tiempo_corto(monkeypatch):
    import warnings

    import firebase_admin
    from firebase_admin import credentials, messaging

    sent = []
    monkeypatch.setattr(credentials, "Certificate", lambda source: ("certificado", source))
    monkeypatch.setattr(firebase_admin, "initialize_app",
                        lambda cert, options, name: SimpleNamespace(cert=cert, options=options, name=name))
    monkeypatch.setattr(messaging, "send_each",
                        lambda messages, app: sent.append((messages, app)) or messaging.BatchResponse([]))

    sender = service.FirebaseSender({"type": "service_account"})
    with warnings.catch_warnings():
        warnings.simplefilter("error", DeprecationWarning)
        sender.send(["token-a", "token-b"], "Solventa", "Su siniestro fue aprobado.",
                    {"type": "CLAIM_STATUS", "claim_number": "SIN-1"})

    messages, app = sent[0]
    assert app.name == "solventa"
    assert app.options == {"httpTimeout": 10}
    assert [m.token for m in messages] == ["token-a", "token-b"]
    assert all(m.data == {"type": "CLAIM_STATUS", "claim_number": "SIN-1", "title": "Solventa",
                          "body": "Su siniestro fue aprobado."} for m in messages)
    assert all(m.android.priority == "high" for m in messages)


def firebase_sender_answering(monkeypatch, *exceptions):
    """Emisor real con un send_each falso que responde un BatchResponse, un resultado por token."""
    import firebase_admin
    from firebase_admin import credentials, messaging

    monkeypatch.setattr(credentials, "Certificate", lambda source: source)
    monkeypatch.setattr(firebase_admin, "initialize_app", lambda cert, options, name: SimpleNamespace(name=name))
    monkeypatch.setattr(messaging, "send_each", lambda messages, app: messaging.BatchResponse([
        messaging.SendResponse(None if exc else {"name": "projects/p/messages/1"}, exc) for exc in exceptions
    ]))
    sender = service.FirebaseSender({"type": "service_account"})
    tokens = [f"token-{i}" for i in range(len(exceptions))]
    return lambda: sender.send(tokens, "Solventa", "Texto", {"type": "CLAIM_STATUS"})


def test_si_todos_los_tokens_fallan_por_algo_pasajero_el_envio_lanza_para_reintentar(monkeypatch):
    from firebase_admin import exceptions, messaging

    send = firebase_sender_answering(
        monkeypatch, exceptions.UnavailableError("caído"), exceptions.InternalError("interno"),
        messaging.UnregisteredError("no registrado"),
    )

    with pytest.raises(service.PushDeliveryError):
        send()


@pytest.mark.parametrize("error", ["QuotaExceededError", "ResourceExhaustedError"])
def test_un_429_de_firebase_es_pasajero_y_se_reintenta(monkeypatch, error):
    from firebase_admin import exceptions, messaging

    exception_class = getattr(messaging, error, None) or getattr(exceptions, error)
    send = firebase_sender_answering(monkeypatch, exception_class("demasiadas solicitudes"))

    with pytest.raises(service.PushDeliveryError):
        send()


def test_una_excepcion_de_red_sin_ningun_exito_tambien_se_reintenta(monkeypatch):
    send = firebase_sender_answering(monkeypatch, ConnectionError("sin red"), TimeoutError("tiempo agotado"))

    with pytest.raises(service.PushDeliveryError):
        send()


@pytest.mark.parametrize("errors", [
    ("UnauthenticatedError", "UnauthenticatedError"),
    ("PermissionDeniedError",),
    ("ThirdPartyAuthError", "ThirdPartyAuthError"),
    ("SenderIdMismatchError", "SenderIdMismatchError"),
    ("UnauthenticatedError", "SenderIdMismatchError"),
])
def test_una_configuracion_rota_que_afecta_a_todos_los_tokens_se_reintenta_y_no_borra_tokens(monkeypatch, errors):
    from firebase_admin import exceptions, messaging

    send = firebase_sender_answering(
        monkeypatch, *[(getattr(messaging, e, None) or getattr(exceptions, e))("configuración") for e in errors]
    )

    with pytest.raises(service.PushDeliveryError):
        send()


def test_si_ningun_token_recibe_el_aviso_se_registra_la_clase_del_error_sin_tokens(monkeypatch, caplog):
    from firebase_admin import exceptions, messaging

    send = firebase_sender_answering(
        monkeypatch, messaging.UnregisteredError("no registrado"), exceptions.InvalidArgumentError("inválido"),
    )

    with caplog.at_level("WARNING", logger="solventa.notifications"):
        send()

    assert [r.getMessage() for r in caplog.records] == [
        "Ningún teléfono recibió el push (InvalidArgumentError, UnregisteredError)"]
    assert "token-" not in caplog.text


def test_un_envio_con_algun_exito_no_registra_advertencia(monkeypatch, caplog):
    from firebase_admin import messaging

    send = firebase_sender_answering(monkeypatch, None, messaging.UnregisteredError("no registrado"))

    with caplog.at_level("WARNING", logger="solventa.notifications"):
        send()

    assert caplog.records == []


def test_los_tokens_no_registrados_mal_formados_o_de_otro_proyecto_no_se_reintentan_y_se_devuelven(monkeypatch):
    from firebase_admin import exceptions, messaging

    send = firebase_sender_answering(
        monkeypatch, messaging.UnregisteredError("no registrado"), exceptions.InvalidArgumentError("inválido"),
        messaging.SenderIdMismatchError("otro proyecto"), None, exceptions.NotFoundError("otro"),
    )

    assert send() == ["token-0", "token-1", "token-2"]


def test_un_exito_parcial_no_se_reintenta_para_no_duplicar(monkeypatch):
    from firebase_admin import exceptions

    send = firebase_sender_answering(monkeypatch, None, exceptions.UnavailableError("caído"))

    assert send() == []


async def test_los_tokens_no_registrados_se_borran_despues_del_envio():
    executed = []

    class Nested:
        async def __aenter__(self):
            return self

        async def __aexit__(self, *args):
            return False

    class Session:
        def begin_nested(self):
            return Nested()

        async def get(self, model, key):
            return None

        async def scalars(self, stmt):
            return ["token-viejo", "token-nuevo"]

        async def execute(self, stmt):
            executed.append(str(stmt))

    class Sender:
        def send(self, tokens, title, body, data):
            return ["token-viejo"]

    notifications = service.NotificationService(lambda: Sender())
    sent = await notifications.notify(Session(), "1020304050", "T", "B", {}, strict=True)

    assert sent is True
    assert any(sql.startswith("DELETE FROM push_tokens") for sql in executed)


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

    async def remind_renewals(session):
        calls.append(("avisar", session))

    monkeypatch.setattr(main, "AsyncSessionLocal", Session)
    monkeypatch.setattr(main.policy_operations, "expire_due", expire_due)
    monkeypatch.setattr(main.policy_operations, "remind_renewals", remind_renewals)

    await main.run_policy_lifecycle()

    assert calls == [("vencer", "sesion"), ("avisar", "sesion")]


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


@pytest.mark.parametrize("url, endpoint, configured", [
    (None, None, False),
    ("https://sqs.us-east-1.amazonaws.com/000000000000/eventos", None, True),
    (None, "http://localhost:4566", True),
])
def test_el_consumidor_solo_arranca_con_la_cola_o_el_emulador(monkeypatch, url, endpoint, configured):
    from app.core import outbox

    monkeypatch.setattr(settings, "SQS_EVENTS_URL", url)
    monkeypatch.setattr(settings, "AWS_ENDPOINT_URL", endpoint)

    assert outbox.events_queue_configured() is configured


async def test_sin_cola_configurada_el_arranque_no_lanza_el_consumidor(monkeypatch):
    started = []
    real_create_task = asyncio.create_task

    def create_task(coro):
        started.append(coro.__qualname__)
        return real_create_task(coro)

    async def nothing():
        return None

    monkeypatch.setattr(main, "upgrade_database", nothing)
    monkeypatch.setattr(main, "events_queue_configured", lambda: False)
    monkeypatch.setattr(main.asyncio, "create_task", create_task)
    monkeypatch.setattr(main, "AsyncSessionLocal", lambda: (_ for _ in ()).throw(RuntimeError("sin base")))

    async with main.lifespan(main.app):
        pass

    assert "consume_outbox" not in started
    assert {"daily_policy_lifecycle", "publish_outbox"} <= set(started)


async def test_con_la_cola_configurada_el_arranque_lanza_el_consumidor_y_al_apagar_espera_las_tareas(monkeypatch):
    started = []
    real_create_task = asyncio.create_task

    def create_task(coro):
        started.append(coro.__qualname__)
        task = real_create_task(coro)
        tasks.append(task)
        return task

    async def nothing(*args, **kwargs):
        return None

    tasks = []
    monkeypatch.setattr(main, "upgrade_database", nothing)
    monkeypatch.setattr(main, "events_queue_configured", lambda: True)
    monkeypatch.setattr(main.outbox_consumer, "consume", nothing)
    monkeypatch.setattr(main.asyncio, "create_task", create_task)
    monkeypatch.setattr(main, "AsyncSessionLocal", lambda: (_ for _ in ()).throw(RuntimeError("sin base")))

    async with main.lifespan(main.app):
        pass

    assert "consume_outbox" in started
    assert all(task.done() for task in tasks)


async def test_una_falla_al_leer_la_cola_no_detiene_el_consumidor(monkeypatch, capsys):
    async def failing(sessions):
        raise RuntimeError("cola caída con detalle interno")

    async def stop(seconds):
        raise asyncio.CancelledError

    monkeypatch.setattr(main.outbox_consumer, "consume", failing)
    monkeypatch.setattr(main.asyncio, "sleep", stop)

    with pytest.raises(asyncio.CancelledError):
        await main.consume_outbox()

    out = capsys.readouterr().out
    assert "no se pudo leer la cola de eventos: RuntimeError" in out
    assert "detalle interno" not in out


def test_el_outbox_de_core_no_importa_modulos():
    import ast
    from pathlib import Path

    from app.core import outbox

    tree = ast.parse(Path(outbox.__file__).read_text(encoding="utf-8"))
    imported = [n.module for n in ast.walk(tree) if isinstance(n, ast.ImportFrom) and n.module]
    imported += [a.name for n in ast.walk(tree) if isinstance(n, ast.Import) for a in n.names]

    assert not [name for name in imported if name.startswith(("app.modules", "app.bff"))]


class ListQueue:
    def __init__(self, messages):
        self.messages, self.deleted, self.retried = messages, [], []

    def receive(self, max_messages, wait_seconds):
        return self.messages

    def delete(self, receipt_handle):
        self.deleted.append(receipt_handle)

    def retry_later(self, receipt_handle, seconds):
        self.retried.append((receipt_handle, seconds))


def message(receipt, receives, event_type="ClaimFiled"):
    import json
    import uuid

    return {"ReceiptHandle": receipt, "Attributes": {"ApproximateReceiveCount": str(receives)},
            "Body": json.dumps({"message_id": str(uuid.uuid4()), "event_type": event_type})}


async def test_la_espera_entre_reintentos_crece_con_cada_recepcion_hasta_15_minutos():
    from app.core import outbox

    queue = ListQueue([message(f"r{n}", n) for n in (1, 2, 3, 4, 5, 6, 8)] + [{"ReceiptHandle": "r0", "Body": "{}"}])
    consumer = outbox.OutboxConsumer(lambda: queue)

    async def failing(sessions, body):
        return outbox.RETRY if body != "{}" else outbox.DONE

    consumer.process = failing
    await consumer.consume(sessions=None, wait_seconds=0)

    assert queue.retried == [("r1", 30), ("r2", 60), ("r3", 120), ("r4", 240), ("r5", 480), ("r6", 900), ("r8", 900)]
    assert queue.deleted == ["r0"]
    assert sum(consumer.retry_delay(n) for n in range(1, 9)) == 3630


async def test_un_tipo_ignorado_se_borra_y_uno_sin_manejador_se_aplaza_al_maximo_hasta_la_dlq():
    from app.core import outbox

    queue = ListQueue([message("r1", 1, "PolicyIssued"), message("r2", 1, "PaymentApproved"),
                       message("r3", 1, "TipoNuevo")])

    done = await outbox.OutboxConsumer(lambda: queue).consume(sessions=None, wait_seconds=0)

    assert done == 2
    assert queue.deleted == ["r1", "r2"]
    assert queue.retried == [("r3", outbox.MAX_RETRY_SECONDS)]
