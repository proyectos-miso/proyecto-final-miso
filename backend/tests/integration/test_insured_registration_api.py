"""Registro desde la app con documento y selfie contra PostgreSQL y el emulador de S3 (mejora FE-MOV-01)."""
import asyncio
import json
import logging

import pytest
from httpx import AsyncClient
from sqlalchemy import func, select

from app.core.storage import get_document_storage
from app.main import app
from app.modules.identity.adapters import SimulatedIdentityVerifier, get_identity_verifier
from app.modules.identity.db_models import IdentityVerificationDB, InsuredAccountDB, LoginAttemptDB
from app.modules.identity.ports import REJECTED, VerificationResult
from app.modules.identity.service_registration import insured_registration_service
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
    s3 = await bucket()
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
    assert await identity_keys(storage) == []
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
    assert await identity_keys(storage) == []


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
    assert await identity_keys(storage) == []


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
    assert await identity_keys(storage) == []
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
    assert await identity_keys(storage) == []
    account = await account_of(postgres_session, "1098700028")
    assert (account.verification_status, account.verified_at is not None) == ("APPROVED", True)
    verification = await verification_of(postgres_session, "1098700028")
    assert (verification.status, verification.s3_keys) == ("APPROVED", None)


async def test_la_verificacion_diferida_rechaza_cierra_la_sesion_y_borra_las_imagenes(
    async_client, postgres_session, storage, verifier
):
    code = await code_for(async_client, "1098700038")
    registered = await register(async_client, body("1098700038", code))

    await insured_registration_service.decide_pending(postgres_session, storage, RejectingVerifier())
    refresh = await async_client.post("/api/v1/auth/refresh", json={"refresh_token": registered.json()["refresh_token"]})
    login_code = await async_client.post("/api/v1/auth/otp", json={"email": "registro.1098700038@example.com"})
    login = await async_client.post("/api/v1/auth/login", json={
        "email": "registro.1098700038@example.com", "password": PASSWORD, "otp_code": login_code.json()["debug_code"],
    })

    assert await identity_keys(storage) == []
    account = await account_of(postgres_session, "1098700038")
    assert (account.verification_status, account.token_version) == ("REJECTED", 1)
    assert (refresh.status_code, login.status_code) == (401, 401)


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
    assert await identity_keys(storage) == []
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
