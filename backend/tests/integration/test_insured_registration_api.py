"""Registro desde la app con documento y selfie contra PostgreSQL y el emulador de S3 (mejora FE-MOV-01)."""
import asyncio
import json
import logging
from datetime import datetime, timedelta, timezone

import pytest
from httpx import AsyncClient
from sqlalchemy import delete, func, select, update

from app.core.config import settings
from app.core.security import create_access_token
from app.core.storage import StorageUnavailableError, get_document_storage
from app.main import app
from app.modules.identity.adapters import SimulatedIdentityVerifier, get_identity_verifier
from app.modules.identity.db_models import IdentityVerificationDB, InsuredAccountDB, LoginAttemptDB
from app.modules.identity.ports import REJECTED, VerificationResult
from app.modules.identity.service import FAILURE_RESULTS, PURPOSE_REGISTRATION, insured_auth_service
from app.modules.identity.service_registration import InsuredRegistrationService, insured_registration_service
from app.modules.profile.models import ConsentDB
from app.modules.profile.service import profile_service
from tests.conftest import INSURED_EMAIL
from tests.integration.claims_fixtures import JPEG, bucket

pytestmark = pytest.mark.integration

OTP_URL = "/api/v1/auth/register/otp"
URL = "/api/v1/auth/register"
PHONE = "3000000000"
PASSWORD = "Registro2026"
APPROVED_DOC = "1098765432"
PENDING_DOC = "1098765438"
REJECTED_DOC = "1098765439"


@pytest.fixture
async def storage():
    # Como el bucket de documentos en AWS: con versionado (Terraform, HA-08).
    s3 = await bucket()
    await asyncio.to_thread(
        s3.client.put_bucket_versioning, Bucket=s3.bucket, VersioningConfiguration={"Status": "Enabled"}
    )
    app.dependency_overrides[get_document_storage] = lambda: s3
    yield s3
    app.dependency_overrides.pop(get_document_storage, None)


@pytest.fixture
def verifier():
    def use(fake):
        app.dependency_overrides[get_identity_verifier] = lambda: fake
    use(SimulatedIdentityVerifier())
    yield use
    app.dependency_overrides.pop(get_identity_verifier, None)


async def identity_keys(storage) -> list:
    listed = await asyncio.to_thread(storage.client.list_objects_v2, Bucket=storage.bucket, Prefix="identidad/")
    return [item["Key"] for item in listed.get("Contents", [])]


async def identity_versions(storage) -> list:
    """Versiones y marcadores de borrado: con versionado, borrar la clave no basta para que la imagen desaparezca."""
    listed = await asyncio.to_thread(storage.client.list_object_versions, Bucket=storage.bucket, Prefix="identidad/")
    return [item["Key"] for item in listed.get("Versions", []) + listed.get("DeleteMarkers", [])]


async def code_for(client: AsyncClient, document: str, phone: str = PHONE) -> str:
    response = await client.post(OTP_URL, json={"document_type": "CC", "document_number": document, "phone": phone})
    assert response.status_code == 200
    return response.json()["debug_code"]


def body(document: str, code: str, **changes) -> dict:
    data = {
        "email": f"registro.{document}@example.com", "full_name": "Ana Prueba Registro", "document_type": "CC",
        "document_number": document, "phone": PHONE, "password": PASSWORD, "otp_code": code,
        "terms_accepted": True, "biometric_consent_accepted": True,
    }
    data.update(changes)
    return data


async def register(client: AsyncClient, data: dict, front: bytes = JPEG, selfie: bytes = JPEG):
    files = {"document_front": ("documento.jpg", front, "image/jpeg"), "selfie": ("selfie.jpg", selfie, "image/jpeg")}
    return await client.post(URL, data={"data": json.dumps(data)}, files=files)


async def accounts(session, document: str) -> int:
    return await session.scalar(
        select(func.count()).select_from(InsuredAccountDB).where(InsuredAccountDB.document_number == document)
    )


async def verification_of(session, document: str) -> IdentityVerificationDB:
    session.expire_all()
    account = (await session.execute(
        select(InsuredAccountDB).where(InsuredAccountDB.document_number == document)
    )).scalars().one()
    return (await session.execute(
        select(IdentityVerificationDB).where(IdentityVerificationDB.user_id == account.user_id)
    )).scalars().one()


async def test_pide_el_codigo_de_registro_con_un_documento_y_un_celular_validos(async_client, postgres_session):
    response = await async_client.post(
        OTP_URL, json={"document_type": "CC", "document_number": APPROVED_DOC, "phone": PHONE}
    )
    invalid = [
        (await async_client.post(OTP_URL, json=payload)).status_code
        for payload in (
            {"document_type": "TI", "document_number": APPROVED_DOC, "phone": PHONE},
            {"document_type": "CC", "document_number": "12AB45", "phone": PHONE},
            {"document_type": "CC", "document_number": APPROVED_DOC, "phone": "1234"},
            {"document_type": "CC", "document_number": APPROVED_DOC, "phone": PHONE, "email": "x@example.com"},
        )
    ]

    assert response.status_code == 200
    assert response.json()["debug_code"].isdigit()
    assert invalid == [422, 422, 422, 422]


async def test_el_codigo_de_registro_tiene_limite_por_documento(async_client, postgres_session):
    await code_for(async_client, "1098700001")

    again = await async_client.post(
        OTP_URL, json={"document_type": "CC", "document_number": "1098700001", "phone": PHONE}
    )

    assert again.status_code == 429
    assert int(again.headers["Retry-After"]) > 0


async def test_aprobada_crea_la_cuenta_abre_la_sesion_y_no_guarda_imagenes(
    async_client, postgres_session, storage, verifier
):
    code = await code_for(async_client, APPROVED_DOC)

    response = await register(async_client, body(APPROVED_DOC, code))

    assert response.status_code == 201
    payload = response.json()
    assert payload["verification_status"] == "APPROVED"
    assert payload["access_token"] and payload["refresh_token"] and payload["full_name"] == "Ana Prueba Registro"
    assert response.headers["Cache-Control"] == "no-store"
    assert await identity_versions(storage) == []
    verification = await verification_of(postgres_session, APPROVED_DOC)
    assert (verification.status, verification.s3_keys) == ("APPROVED", None)
    policies = await async_client.get(
        "/api/v1/insured/policies", headers={"Authorization": f"Bearer {payload['access_token']}"}
    )
    assert policies.status_code == 200


async def test_pendiente_crea_la_cuenta_con_202_y_guarda_las_imagenes_bajo_identidad(
    async_client, postgres_session, storage, verifier
):
    code = await code_for(async_client, PENDING_DOC)

    response = await register(async_client, body(PENDING_DOC, code))

    assert response.status_code == 202
    assert response.json()["verification_status"] == "PENDING"
    verification = await verification_of(postgres_session, PENDING_DOC)
    assert verification.status == "PENDING"
    assert sorted(await identity_keys(storage)) == sorted(verification.s3_keys)
    assert all(key.startswith(f"identidad/{verification.user_id}/") for key in verification.s3_keys)
    assert len(verification.s3_keys) == 2
    policies = await async_client.get(
        "/api/v1/insured/policies", headers={"Authorization": f"Bearer {response.json()['access_token']}"}
    )
    assert (policies.status_code, policies.json()["detail"]) == (403, "Estamos verificando su identidad.")


async def test_rechazada_responde_422_sin_cuenta_ni_imagenes(async_client, postgres_session, storage, verifier):
    code = await code_for(async_client, REJECTED_DOC)

    response = await register(async_client, body(REJECTED_DOC, code))

    assert (response.status_code, response.json()) == (
        422, {"detail": "No pudimos verificar su identidad.", "code": "IDENTITY_REJECTED"}
    )
    assert await accounts(postgres_session, REJECTED_DOC) == 0
    assert await identity_versions(storage) == []


async def test_correo_o_documento_repetidos_responden_409_sin_decir_cual(
    async_client, postgres_session, storage, verifier, seeded_insured
):
    code = await code_for(async_client, "1020304050")
    repeated_document = await register(async_client, body("1020304050", code, email="otra.persona@example.com"))
    code = await code_for(async_client, "1098700002")
    repeated_email = await register(async_client, body("1098700002", code, email=INSURED_EMAIL.upper()))

    assert repeated_document.status_code == repeated_email.status_code == 409
    assert repeated_document.json() == repeated_email.json() == {
        "detail": "No pudimos crear la cuenta con esos datos.", "code": "DUPLICATE"
    }
    assert await accounts(postgres_session, "1098700002") == 0


async def test_clave_debil_codigo_invalido_y_jpeg_falso_responden_422(async_client, postgres_session, storage, verifier):
    code = await code_for(async_client, "1098700003")

    weak = await register(async_client, body("1098700003", code, password="solo-letras"))
    fake_jpeg = await register(async_client, body("1098700003", code), front=b"%PDF-1.7 no es una foto")
    wrong_code = await register(async_client, body("1098700003", "000000" if code != "000000" else "111111"))
    other_phone = await register(async_client, body("1098700003", code, phone="3110000000"))
    no_consent = await register(async_client, body("1098700003", code, biometric_consent_accepted=False))
    too_large = await register(async_client, body("1098700003", code), selfie=JPEG + b"0" * 2_000_000)

    assert (weak.status_code, fake_jpeg.status_code, wrong_code.status_code) == (422, 422, 422)
    assert (other_phone.status_code, no_consent.status_code, too_large.status_code) == (422, 422, 413)
    assert (weak.json()["code"], fake_jpeg.json()["code"], no_consent.json()["code"]) == (
        "WEAK_PASSWORD", "INVALID_PHOTO", "INVALID_DATA"
    )
    assert "contraseña" in weak.json()["detail"]
    assert wrong_code.json() == {"detail": "El código no es válido o ya venció. Pida uno nuevo.", "code": "INVALID_CODE"}
    assert await accounts(postgres_session, "1098700003") == 0
    assert await identity_versions(storage) == []


async def test_el_registro_tiene_limite_de_intentos_por_documento(async_client, postgres_session, storage, verifier):
    await code_for(async_client, "1098700004")

    statuses = [
        (await register(async_client, body("1098700004", f"{attempt:06d}"))).status_code for attempt in range(6)
    ]

    assert statuses[:5] == [422] * 5
    assert statuses[5] == 429


async def test_guarda_el_consentimiento_biometrico_que_no_vale_como_open_finance(
    async_client, postgres_session, storage, verifier
):
    code = await code_for(async_client, "1098700005")
    await register(async_client, body("1098700005", code))

    consent = (await postgres_session.execute(
        select(ConsentDB).where(ConsentDB.document_number == "1098700005")
    )).scalars().one()

    assert (consent.purpose, consent.channel, consent.status) == ("biometric_verification", "app", "GRANTED")
    assert len(consent.consent_hash) == 64
    assert await profile_service.get_consent_async(postgres_session, "1098700005") is None


async def test_revocar_el_consentimiento_borra_las_imagenes_pendientes(async_client, postgres_session, storage, verifier):
    code = await code_for(async_client, "1098700018")
    registered = await register(async_client, body("1098700018", code))
    headers = {"Authorization": f"Bearer {registered.json()['access_token']}"}
    assert len(await identity_keys(storage)) == 2

    response = await async_client.post("/api/v1/insured/consents/biometric/revoke", headers=headers)
    anonymous = await async_client.post("/api/v1/insured/consents/biometric/revoke")

    assert response.status_code == 200
    assert anonymous.status_code == 401
    assert await identity_versions(storage) == []
    verification = await verification_of(postgres_session, "1098700018")
    assert (verification.status, verification.s3_keys) == ("REVOKED", None)
    consent = (await postgres_session.execute(
        select(ConsentDB).where(ConsentDB.document_number == "1098700018")
    )).scalars().one()
    assert consent.status == "REVOKED"


class RejectingVerifier(SimulatedIdentityVerifier):
    async def decide(self, reference: str) -> VerificationResult:
        return VerificationResult(REJECTED, reference)


async def account_of(session, document: str) -> InsuredAccountDB:
    session.expire_all()
    return (await session.execute(
        select(InsuredAccountDB).where(InsuredAccountDB.document_number == document)
    )).scalars().one()


async def test_la_verificacion_diferida_aprueba_y_borra_las_imagenes(async_client, postgres_session, storage, verifier):
    code = await code_for(async_client, "1098700028")
    await register(async_client, body("1098700028", code))
    assert len(await identity_keys(storage)) == 2

    decided = await insured_registration_service.decide_pending(postgres_session, storage, SimulatedIdentityVerifier())

    assert decided == 1
    assert await identity_versions(storage) == []
    account = await account_of(postgres_session, "1098700028")
    assert (account.verification_status, account.verified_at is not None) == ("APPROVED", True)
    verification = await verification_of(postgres_session, "1098700028")
    assert (verification.status, verification.s3_keys) == ("APPROVED", None)


async def test_la_verificacion_diferida_rechaza_cierra_la_sesion_y_borra_las_imagenes(
    async_client, postgres_session, storage, verifier
):
    code = await code_for(async_client, "1098700038")
    registered = await register(async_client, body("1098700038", code))
    headers = {"Authorization": f"Bearer {registered.json()['access_token']}"}
    device = (await async_client.post("/api/v1/auth/devices", headers=headers)).json()

    await insured_registration_service.decide_pending(postgres_session, storage, RejectingVerifier())
    refresh = await async_client.post("/api/v1/auth/refresh", json={"refresh_token": registered.json()["refresh_token"]})
    login = await async_client.post("/api/v1/auth/login", json={
        "email": "registro.1098700038@example.com", "password": PASSWORD, "otp_code": "123456",
    })
    biometric = await async_client.post("/api/v1/auth/biometric", json=device)

    assert await identity_versions(storage) == []
    account = await account_of(postgres_session, "1098700038")
    assert (account.verification_status, account.token_version) == ("REJECTED", 1)
    assert (refresh.status_code, login.status_code, biometric.status_code) == (401, 401, 401)
    consent = (await postgres_session.execute(
        select(ConsentDB).where(ConsentDB.document_number == "1098700038")
    )).scalars().one()
    assert consent.status == "REVOKED"


async def forget_attempts(session) -> None:
    """Como si hubiera pasado la espera entre códigos del mismo correo o documento."""
    await session.execute(delete(LoginAttemptDB))
    await session.commit()


async def test_una_cuenta_rechazada_responde_como_inexistente_al_pedir_codigo_o_recuperar_la_clave(
    async_client, postgres_session, storage, verifier
):
    code = await code_for(async_client, "1098700058")
    await register(async_client, body("1098700058", code))
    await insured_registration_service.decide_pending(postgres_session, storage, RejectingVerifier())
    rejected, unknown = "registro.1098700058@example.com", "nadie.1098700058@example.com"

    responses = {}
    for email in (rejected, unknown):
        otp = await async_client.post("/api/v1/auth/otp", json={"email": email})
        await forget_attempts(postgres_session)
        forgot = await async_client.post("/api/v1/auth/password/forgot", json={"email": email})
        reset = await async_client.post(
            "/api/v1/auth/password/reset", json={"email": email, "code": "123456", "new_password": "OtraClave2026"}
        )
        responses[email] = [(r.status_code, r.json()) for r in (otp, forgot, reset)]

    assert responses[rejected] == responses[unknown]
    assert responses[rejected][0][1]["debug_code"] is None
    assert responses[rejected][1][1]["debug_code"] is None


class SlowVerifier(SimulatedIdentityVerifier):
    def __init__(self):
        super().__init__(delay_seconds=2.0)


async def test_si_el_proveedor_tarda_mas_de_400_ms_queda_pendiente_y_se_decide_despues(
    async_client, postgres_session, storage, verifier
):
    verifier(SlowVerifier())
    code = await code_for(async_client, APPROVED_DOC)

    response = await register(async_client, body(APPROVED_DOC, code))

    assert response.status_code == 202
    verification = await verification_of(postgres_session, APPROVED_DOC)
    assert verification.provider_ref is None
    assert len(await identity_keys(storage)) == 2

    decided = await insured_registration_service.decide_pending(postgres_session, storage, SimulatedIdentityVerifier())

    assert decided == 1
    assert await identity_versions(storage) == []
    assert (await verification_of(postgres_session, APPROVED_DOC)).status == "APPROVED"


async def test_ningun_registro_lleva_datos_personales(async_client, postgres_session, storage, verifier, caplog):
    caplog.set_level(logging.INFO, logger="uvicorn.error")
    code = await code_for(async_client, "1098700048")
    await register(async_client, body("1098700048", code))
    rejected_code = await code_for(async_client, REJECTED_DOC)
    await register(async_client, body(REJECTED_DOC, rejected_code))

    audited = (await postgres_session.execute(select(LoginAttemptDB.email))).scalars().all()

    for personal in ("1098700048", REJECTED_DOC, "registro.1098700048@example.com", "Ana Prueba Registro", PHONE, code):
        assert personal not in caplog.text
        assert all(personal not in subject for subject in audited)
    assert audited and all(subject.startswith("registro:") for subject in audited)
    assert "Registro desde la app con verificación PENDING" in caplog.text


# Correcciones de la revisión independiente (mejora FE-MOV-01).

async def test_borrar_las_imagenes_quita_todas_las_versiones_y_marcadores_del_prefijo(storage):
    await storage.put_bytes("identidad/u1/v1/selfie.jpg", JPEG, "image/jpeg")
    await storage.put_bytes("identidad/u1/v1/selfie.jpg", JPEG + b"otra", "image/jpeg")
    await asyncio.to_thread(storage.client.delete_object, Bucket=storage.bucket, Key="identidad/u1/v1/selfie.jpg")
    await storage.put_bytes("identidad/u1/v2/selfie.jpg", JPEG, "image/jpeg")
    assert len(await identity_versions(storage)) == 4

    deleted = await storage.purge("identidad/u1/v1/")

    assert deleted == 3
    assert await identity_versions(storage) == ["identidad/u1/v2/selfie.jpg"]
    with pytest.raises(ValueError):
        await storage.purge("identidad/u1/v2")


async def test_con_el_registro_apagado_las_dos_rutas_responden_404(
    async_client, postgres_session, storage, verifier, monkeypatch
):
    monkeypatch.setattr(settings, "REGISTRATION_ENABLED", False)

    otp = await async_client.post(OTP_URL, json={"document_type": "CC", "document_number": APPROVED_DOC, "phone": PHONE})
    created = await register(async_client, body(APPROVED_DOC, "123456"))

    assert (otp.status_code, created.status_code) == (404, 404)
    assert await accounts(postgres_session, APPROVED_DOC) == 0


async def backdate(session, document: str, days: int) -> None:
    account = await account_of(session, document)
    await session.execute(
        update(IdentityVerificationDB)
        .where(IdentityVerificationDB.user_id == account.user_id)
        .values(created_at=IdentityVerificationDB.created_at - timedelta(days=days))
    )
    await session.commit()


async def register_again(client: AsyncClient, session, document: str, suffix: str):
    """Otra persona, con otro correo y otro celular, se registra con el mismo documento pasada la espera del código."""
    await forget_attempts(session)
    phone = f"311000{suffix}"
    code = await code_for(client, document, phone=phone)
    return await register(client, body(document, code, email=f"otra.{document}@example.com", phone=phone))


async def test_una_pendiente_de_mas_de_30_dias_vence_borra_sus_imagenes_y_libera_el_documento(
    async_client, postgres_session, storage, verifier
):
    code = await code_for(async_client, "1098700068")
    registered = await register(async_client, body("1098700068", code))
    await backdate(postgres_session, "1098700068", 31)

    undecided = await insured_registration_service.decide_pending(postgres_session, storage, SimulatedIdentityVerifier())
    expired = await insured_registration_service.expire_pending(postgres_session, storage)
    versions_after_expiry = await identity_versions(storage)
    refresh = await async_client.post("/api/v1/auth/refresh", json={"refresh_token": registered.json()["refresh_token"]})
    again = await register_again(async_client, postgres_session, "1098700068", "0068")

    assert (undecided, expired) == (0, 1)
    assert versions_after_expiry == []
    assert refresh.status_code == 401
    postgres_session.expire_all()
    old = (await postgres_session.execute(
        select(InsuredAccountDB).where(InsuredAccountDB.email == "registro.1098700068@example.com")
    )).scalars().one()
    assert (old.verification_status, old.token_version) == ("EXPIRED", 1)
    assert again.status_code == 202


async def test_una_pendiente_vencida_sin_procesar_tampoco_ocupa_el_documento(
    async_client, postgres_session, storage, verifier
):
    code = await code_for(async_client, "1098700078")
    await register(async_client, body("1098700078", code))
    await backdate(postgres_session, "1098700078", 31)

    again = await register_again(async_client, postgres_session, "1098700078", "0078")

    assert again.status_code == 202


async def test_una_cuenta_rechazada_no_ocupa_el_documento(async_client, postgres_session, storage, verifier):
    code = await code_for(async_client, "1098700088")
    await register(async_client, body("1098700088", code))
    await insured_registration_service.decide_pending(postgres_session, storage, RejectingVerifier())

    again = await register_again(async_client, postgres_session, "1098700088", "0088")

    assert again.status_code == 202


DATA_ROUTES = [
    ("GET", "/api/v1/insured/policies", "access_token"),
    ("GET", "/api/v1/insured/claims", "access_token"),
    ("GET", "/api/v1/insured/policies/POL-2026-00001/caratula", "access_token"),
    ("POST", "/api/v1/insured/policies/POL-2026-00001/renewal", "access_token"),
    ("GET", "/api/v1/insured/devices/push-preferences", "access_token"),
    ("PUT", "/api/v1/insured/devices/push-preferences", "access_token"),
    ("POST", "/api/v1/insured/devices/push-token", "access_token"),
    ("POST", "/api/v1/insured/claims", "access_token"),
    ("POST", "/api/v1/insured/claims", "report_token"),
]


@pytest.mark.parametrize("method, url, token", DATA_ROUTES)
async def test_una_cuenta_pendiente_no_alcanza_ninguna_ruta_de_datos(
    async_client, postgres_session, storage, verifier, method, url, token
):
    code = await code_for(async_client, PENDING_DOC)
    tokens = (await register(async_client, body(PENDING_DOC, code))).json()
    headers = {"Authorization": "Bearer " + tokens[token], "Idempotency-Key": "registro-pendiente-0001"}
    form = {"data": {"report": "{}"}} if url.endswith("/claims") and method == "POST" else {}

    response = await async_client.request(method, url, headers=headers, **form)

    assert (response.status_code, response.json()["detail"]) == (403, "Estamos verificando su identidad.")


async def test_el_codigo_de_registro_tiene_limite_por_telefono(async_client, postgres_session):
    for document in ("1098700101", "1098700102", "1098700103"):
        await code_for(async_client, document)

    fourth = await async_client.post(
        OTP_URL, json={"document_type": "CC", "document_number": "1098700104", "phone": PHONE}
    )
    other_phone = await code_for(async_client, "1098700105", phone="3110000105")

    assert fourth.status_code == 429
    assert other_phone.isdigit()
    subjects = (await postgres_session.execute(select(LoginAttemptDB.email))).scalars().all()
    assert all(PHONE not in subject for subject in subjects)


async def test_los_duplicados_y_rechazos_cuentan_en_el_limite_por_ip(async_client, postgres_session, storage, verifier):
    ip = "203.0.113.7"
    now = datetime.now(timezone.utc)
    for result in ("duplicate", "rejected") * 10:
        postgres_session.add(LoginAttemptDB(email="registro:x", ip=ip, channel="register", result=result, created_at=now))
    await postgres_session.commit()
    code = await code_for(async_client, APPROVED_DOC)

    response = await async_client.post(
        URL, data={"data": json.dumps(body(APPROVED_DOC, code))},
        files={"document_front": ("d.jpg", JPEG, "image/jpeg"), "selfie": ("s.jpg", JPEG, "image/jpeg")},
        headers={"X-Forwarded-For": ip},
    )

    assert response.status_code == 429
    assert {"duplicate", "rejected"} <= set(FAILURE_RESULTS)


class BrokenStorage:
    def __init__(self, real):
        self.real = real

    async def put_bytes(self, key, data, content_type):
        raise StorageUnavailableError("caído")

    async def purge(self, prefix):
        return await self.real.purge(prefix)


async def test_si_s3_falla_el_codigo_queda_consumido_y_el_intento_auditado(
    async_client, postgres_session, storage, verifier
):
    code = await code_for(async_client, PENDING_DOC)
    app.dependency_overrides[get_document_storage] = lambda: BrokenStorage(storage)
    failed = await register(async_client, body(PENDING_DOC, code))
    app.dependency_overrides[get_document_storage] = lambda: storage

    retried = await register(async_client, body(PENDING_DOC, code))

    assert failed.status_code == 503
    assert (retried.status_code, retried.json()["code"]) == (422, "INVALID_CODE")
    assert await accounts(postgres_session, PENDING_DOC) == 0
    results = (await postgres_session.execute(
        select(LoginAttemptDB.result).where(LoginAttemptDB.channel == "register")
    )).scalars().all()
    assert "storage_error" in results


async def test_si_la_base_rechaza_el_correo_el_codigo_queda_consumido(
    async_client, postgres_session, storage, verifier, seeded_insured, monkeypatch
):
    async def never_taken(*args):
        return False

    monkeypatch.setattr(InsuredRegistrationService, "_taken", staticmethod(never_taken))
    code = await code_for(async_client, "1098700112")

    duplicate = await register(async_client, body("1098700112", code, email=INSURED_EMAIL))
    retried = await register(async_client, body("1098700112", code))

    assert duplicate.status_code == 409
    assert (retried.status_code, retried.json()["code"]) == (422, "INVALID_CODE")


async def test_el_codigo_de_registro_de_un_telefono_no_borra_el_de_otro(postgres_session):
    victim = await insured_auth_service.request_document_otp(
        postgres_session, "1098700122", PURPOSE_REGISTRATION, binding="CC:+573000000000"
    )
    await insured_auth_service.request_document_otp(
        postgres_session, "1098700122", PURPOSE_REGISTRATION, binding="CC:+573110000000"
    )
    first_consent = await insured_auth_service.request_document_otp(postgres_session, "1098700122", "consent")
    await insured_auth_service.request_document_otp(postgres_session, "1098700122", "consent")

    assert await insured_auth_service.verify_document_otp(
        postgres_session, "1098700122", PURPOSE_REGISTRATION, victim, binding="CC:+573000000000"
    )
    assert not await insured_auth_service.verify_document_otp(postgres_session, "1098700122", "consent", first_consent)


async def test_un_cuerpo_de_mas_de_7_mb_responde_413_antes_de_leer_el_multipart(async_client, postgres_session):
    big = await register(async_client, body(APPROVED_DOC, "123456"), selfie=JPEG + b"0" * (7 * 1024 * 1024))

    async def chunks():
        yield b'--x\r\nContent-Disposition: form-data; name="selfie"; filename="s.jpg"\r\nContent-Type: image/jpeg\r\n\r\n'
        for _ in range(8):
            yield b"0" * (1024 * 1024)

    streamed = await async_client.post(
        URL, content=chunks(), headers={"Content-Type": "multipart/form-data; boundary=x"}
    )

    assert (big.status_code, big.json()["code"]) == (413, "INVALID_PHOTO")
    assert streamed.status_code == 413


async def test_el_consentimiento_biometrico_no_sirve_para_cotizar(async_client, postgres_session, storage, verifier):
    code = await code_for(async_client, "1098700132")
    await register(async_client, body("1098700132", code))
    consent_id = (await postgres_session.execute(
        select(ConsentDB.id).where(ConsentDB.document_number == "1098700132")
    )).scalar_one()
    advisor = create_access_token(subject="asesor@solventa.com", role="advisor")

    quote = await async_client.post(
        "/api/v1/experience/quotes",
        headers={"Authorization": "Bearer " + advisor},
        json={"document_type": "CC", "document_number": "1098700132", "birth_date": "1992-07-20",
              "insured_amount": 120000000.0, "consent_id": consent_id},
    )

    assert quote.status_code == 422
    assert await profile_service.get_consent_by_id_async(postgres_session, consent_id) is None


async def test_la_decision_diferida_procesa_lotes_de_20_cada_uno_con_su_transaccion(
    postgres_session, storage, monkeypatch
):
    created = datetime.now(timezone.utc)
    for index in range(25):
        user_id = f"ins-lote-{index:02d}"
        postgres_session.add(InsuredAccountDB(
            user_id=user_id, email=f"lote.{index}@example.com", full_name="Cuenta Lote", document_type="CC",
            document_number=f"10987002{index:02d}", phone="+573000000000", password_hash="hash", token_version=0,
            verification_status="PENDING",
        ))
        await postgres_session.flush()
        postgres_session.add(IdentityVerificationDB(
            verification_id=f"ver-lote-{index:02d}", user_id=user_id, status="PENDING", provider_ref=f"sim-{index}",
            created_at=created,
        ))
    await postgres_session.commit()
    commits = []
    original = postgres_session.commit

    async def counting_commit():
        commits.append(1)
        await original()

    monkeypatch.setattr(postgres_session, "commit", counting_commit)

    decided = await insured_registration_service.decide_pending(postgres_session, storage, SimulatedIdentityVerifier())

    assert decided == 25
    assert len(commits) == 2
    statuses = (await postgres_session.execute(select(InsuredAccountDB.verification_status))).scalars().all()
    assert set(statuses) == {"APPROVED"}
