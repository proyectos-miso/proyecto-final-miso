"""Registro del asegurado desde la app con foto del documento y selfie (mejora FE-MOV-01).

Las imágenes solo se guardan mientras la verificación está pendiente; con la decisión, la revocación o el vencimiento
se borran todas sus versiones. Los límites y la auditoría usan seudónimos HMAC, nunca el documento ni el teléfono.
"""
import asyncio
import hashlib
import hmac
import logging
import uuid
from dataclasses import dataclass
from datetime import datetime, timedelta
from typing import List, Optional

from sqlalchemy import and_, func, or_, select, text, update
from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import settings
from app.core.security import get_password_hash
from app.core.sms import SmsDeliveryError
from app.core.storage import DocumentStorage, StorageUnavailableError
from app.modules.identity import policy
from app.modules.identity.db_models import IdentityVerificationDB, InsuredAccountDB, InsuredDeviceDB, LoginAttemptDB
from app.modules.identity.models import TokenPair
from app.modules.identity.ports import APPROVED, PENDING, REJECTED, IdentityVerifier, VerificationResult
from app.modules.identity.service import (
    PURPOSE_REGISTRATION,
    VERIFICATION_EXPIRED,
    VERIFICATION_REVOKED,
    InsuredAuthService,
    RateLimitedError,
    SmsUnavailableError,
    WeakPasswordError,
    _normalize,
    insured_auth_service,
)
from app.modules.profile.service import profile_service

VERIFY_TIMEOUT_SECONDS = 0.4
IDENTITY_PREFIX = "identidad"
REVOKED = VERIFICATION_REVOKED
EXPIRED = VERIFICATION_EXPIRED
# Tope de una verificación pendiente: después se vence, se borran sus imágenes y el documento queda libre.
PENDING_TTL = timedelta(days=30)
DECISION_BATCH = 20
# Tres fotos de 2 MB, los datos y el sobrecosto del multipart.
REGISTRATION_MAX_BODY_BYTES = 7 * 1024 * 1024

logger = logging.getLogger("uvicorn.error")


class InvalidRegistrationCodeError(Exception):
    pass


class DuplicateAccountError(Exception):
    """El correo o el documento ya tienen cuenta; la respuesta no dice cuál."""


class IdentityRejectedError(Exception):
    pass


@dataclass(frozen=True)
class NewInsured:
    email: str
    full_name: str
    document_type: str
    document_number: str
    phone: str
    password: str
    otp_code: str


@dataclass(frozen=True)
class IdentityPhotos:
    document_front: bytes
    selfie: bytes
    document_back: Optional[bytes] = None


@dataclass(frozen=True)
class RegistrationOutcome:
    status: str
    tokens: TokenPair


class InsuredRegistrationService:
    def __init__(self, auth: InsuredAuthService = insured_auth_service):
        self._auth = auth

    async def request_otp(
        self, session: AsyncSession, document_type: str, document_number: str, phone: str, ip: Optional[str]
    ) -> str:
        """Código por SMS al teléfono del registro, atado al documento y a ese teléfono."""
        now = self._auth._clock()
        subject = self._subject(document_type, document_number)
        phone_subject = self._phone_subject(phone)
        await self._auth._check_ip(session, ip, now, otp_request=True)
        hour_ago = now - timedelta(hours=1)
        requested = await self._auth._attempt_times(
            session, LoginAttemptDB.email == subject, "otp_request", ("success",), hour_ago
        )
        to_phone = await self._auth._attempt_times(
            session, LoginAttemptDB.email == phone_subject, "otp_request", ("success",), hour_ago
        )
        retry_after = policy.otp_request_retry_after(requested, now) or policy.registration_phone_retry_after(
            to_phone, now
        )
        if retry_after is not None:
            await self._auth._audit(session, subject, None, ip, "otp_request", "rate_limited", now)
            await session.commit()
            raise RateLimitedError(retry_after)
        try:
            code = await self._auth.request_document_otp(
                session, document_number, PURPOSE_REGISTRATION, phone=phone, document_type=document_type,
                binding=self._binding(document_type, phone),
            )
        except SmsDeliveryError as exc:
            await session.rollback()
            raise SmsUnavailableError() from exc
        await self._auth._audit(session, subject, None, ip, "otp_request", "success", now)
        # Sin IP: la fila del documento ya cuenta para el límite por IP.
        await self._auth._audit(session, phone_subject, None, None, "otp_request", "success", now)
        await session.commit()
        return code

    async def register(
        self,
        session: AsyncSession,
        storage: DocumentStorage,
        verifier: IdentityVerifier,
        data: NewInsured,
        photos: IdentityPhotos,
        ip: Optional[str] = None,
    ) -> RegistrationOutcome:
        if not policy.is_strong_password(data.password):
            raise WeakPasswordError()
        now = self._auth._clock()
        email = _normalize(data.email)
        subject = self._subject(data.document_type, data.document_number)
        await self._auth._check_ip(session, ip, now)
        attempts = await self._auth._attempt_times(
            session, LoginAttemptDB.email == subject, "register", None, now - timedelta(hours=1)
        )
        retry_after = policy.registration_retry_after(attempts, now)
        if retry_after is not None:
            raise RateLimitedError(retry_after)

        binding = self._binding(data.document_type, data.phone)
        valid = await self._auth.verify_document_otp(
            session, data.document_number, PURPOSE_REGISTRATION, data.otp_code, data.document_type,
            commit=False, binding=binding,
        )
        if not valid:
            await self._fail(session, subject, ip, "bad_code", now)
            raise InvalidRegistrationCodeError()

        # El documento no tiene índice único: el bloqueo serializa dos registros con el mismo número.
        await session.execute(
            text("SELECT pg_advisory_xact_lock(hashtext(:key))"), {"key": f"registro:{data.document_number}"}
        )
        if await self._taken(session, email, data.document_number, now):
            await self._fail(session, subject, ip, "duplicate", now)
            raise DuplicateAccountError()

        result = await self._verify(
            verifier,
            lambda: verifier.verify(
                data.document_type, data.document_number, data.full_name, photos.document_front, photos.selfie
            ),
        )
        if result.status == REJECTED:
            await self._fail(session, subject, ip, "rejected", now)
            raise IdentityRejectedError()

        account = InsuredAccountDB(
            user_id=str(uuid.uuid4()),
            email=email,
            full_name=data.full_name,
            document_type=data.document_type,
            document_number=data.document_number,
            phone=data.phone,
            password_hash=get_password_hash(data.password),
            token_version=0,
            verification_status=result.status,
            verified_at=now if result.status == APPROVED else None,
        )
        session.add(account)
        try:
            await session.flush()
        except IntegrityError:
            await self._abort(session, data, subject, ip, "duplicate", now)
            raise DuplicateAccountError()
        verification = IdentityVerificationDB(
            verification_id=str(uuid.uuid4()),
            user_id=account.user_id,
            status=result.status,
            provider_ref=result.reference,
            created_at=now,
            decided_at=now if result.status == APPROVED else None,
        )
        session.add(verification)
        profile_service.add_biometric_consent(session, account.user_id, data.document_type, data.document_number, now)

        prefix = self._prefix(account.user_id, verification.verification_id)
        if result.status == PENDING:
            try:
                verification.s3_keys = await self._store(storage, prefix, photos)
            except StorageUnavailableError:
                await self._abort(session, data, subject, ip, "storage_error", now)
                await self._purge_quietly(storage, prefix)
                raise
        await self._auth._audit(session, subject, account.user_id, ip, "register", "success", now)
        tokens = self._auth._issue_tokens(account)
        try:
            await session.commit()
        except IntegrityError:
            await self._abort(session, data, subject, ip, "duplicate", now)
            await self._purge_quietly(storage, prefix)
            raise DuplicateAccountError()
        logger.info("Registro desde la app con verificación %s", result.status)
        return RegistrationOutcome(result.status, tokens)

    async def decide_pending(self, session: AsyncSession, storage: DocumentStorage, verifier: IdentityVerifier) -> int:
        """Verificación diferida en lotes de 20, cada uno en su transacción: pide la decisión de cada pendiente
        vigente y borra sus imágenes. Devuelve cuántas decidió."""
        decided = 0
        seen: List[str] = []
        while True:
            rows = await self._pending_batch(session, seen, expired=False)
            for row in rows:
                seen.append(row.verification_id)
                account = await session.get(InsuredAccountDB, row.user_id)
                result = await self._decide(storage, verifier, row, account)
                if result.status == PENDING:
                    continue
                try:
                    await self._purge(storage, row)
                except StorageUnavailableError:
                    continue
                now = self._auth._clock()
                row.provider_ref = result.reference or row.provider_ref
                if result.status == APPROVED:
                    # La autorización biométrica queda GRANTED como constancia: las imágenes ya no existen y no se
                    # vuelven a tratar; revocarla no cambiaría nada y borraría la prueba de que se autorizó.
                    row.status, row.decided_at, row.s3_keys = APPROVED, now, None
                    account.verification_status, account.verified_at = APPROVED, now
                else:
                    await self._close(session, row, account, REJECTED, now)
                decided += 1
            await session.commit()
            if len(rows) < DECISION_BATCH:
                break
        if decided:
            logger.info("Verificación diferida: %s decididas", decided)
        return decided

    async def expire_pending(self, session: AsyncSession, storage: DocumentStorage) -> int:
        """Vence las pendientes de más de 30 días, en lotes de 20: borra sus imágenes, cierra la cuenta y deja libre el
        documento. Devuelve cuántas venció."""
        expired = 0
        seen: List[str] = []
        while True:
            rows = await self._pending_batch(session, seen, expired=True)
            for row in rows:
                seen.append(row.verification_id)
                try:
                    await self._purge(storage, row)
                except StorageUnavailableError:
                    continue
                account = await session.get(InsuredAccountDB, row.user_id)
                await self._close(session, row, account, EXPIRED, self._auth._clock())
                expired += 1
            await session.commit()
            if len(rows) < DECISION_BATCH:
                break
        if expired:
            logger.info("Verificación diferida: %s vencidas", expired)
        return expired

    async def revoke_biometric_consent(
        self, session: AsyncSession, storage: DocumentStorage, account: InsuredAccountDB
    ) -> int:
        """Revoca la autorización y borra las imágenes pendientes. Sin las imágenes la identidad ya no se puede verificar:
        una cuenta en verificación se cierra como REVOKED, igual que una vencida, y su documento queda libre. Una cuenta
        aprobada sigue abierta. Devuelve cuántas versiones borró."""
        rows = (
            await session.execute(
                select(IdentityVerificationDB)
                .where(IdentityVerificationDB.user_id == account.user_id, IdentityVerificationDB.status == PENDING)
                .with_for_update()
            )
        ).scalars().all()
        deleted = 0
        try:
            for row in rows:
                deleted += await self._purge(storage, row)
        except StorageUnavailableError:
            await session.rollback()
            raise
        now = self._auth._clock()
        for row in rows:
            await self._close(session, row, account, REVOKED, now)
        await profile_service.revoke_biometric_consent(session, account.user_id)
        await session.commit()
        return deleted

    async def _pending_batch(self, session: AsyncSession, seen: List[str], expired: bool) -> list:
        cutoff = self._auth._clock() - PENDING_TTL
        age = IdentityVerificationDB.created_at <= cutoff if expired else IdentityVerificationDB.created_at > cutoff
        return (
            await session.execute(
                select(IdentityVerificationDB)
                .where(IdentityVerificationDB.status == PENDING, age, IdentityVerificationDB.verification_id.notin_(seen))
                .order_by(IdentityVerificationDB.created_at)
                .limit(DECISION_BATCH)
                .with_for_update(skip_locked=True)
            )
        ).scalars().all()

    async def _close(
        self, session: AsyncSession, row: IdentityVerificationDB, account: InsuredAccountDB, status: str, now: datetime
    ) -> None:
        """Rechazada, vencida o revocada: cierra sus sesiones y huellas y revoca la autorización biométrica, que ya no
        tiene uso. Solo cierra una cuenta en verificación: una aprobada nunca se cierra por aquí."""
        row.status, row.decided_at, row.s3_keys = status, now, None
        if account.verification_status != PENDING:
            logger.warning("Se omitió cerrar una cuenta que no está en verificación")
            return
        account.verification_status = status
        account.token_version += 1
        await session.execute(
            update(InsuredDeviceDB)
            .where(InsuredDeviceDB.user_id == account.user_id, InsuredDeviceDB.revoked_at.is_(None))
            .values(revoked_at=now)
        )
        await profile_service.revoke_biometric_consent(session, account.user_id)

    @staticmethod
    async def _taken(session: AsyncSession, email: str, document_number: str, now: datetime) -> bool:
        """El correo con cualquier cuenta (índice único). El documento solo con una cuenta aprobada o con una
        verificación pendiente de menos de 30 días: una rechazada, vencida o revocada no lo ocupa."""
        live_pending = (
            select(IdentityVerificationDB.verification_id)
            .where(
                IdentityVerificationDB.user_id == InsuredAccountDB.user_id,
                IdentityVerificationDB.status == PENDING,
                IdentityVerificationDB.created_at > now - PENDING_TTL,
            )
            .exists()
        )
        document_taken = and_(
            InsuredAccountDB.document_number == document_number,
            or_(
                InsuredAccountDB.verification_status == APPROVED,
                and_(InsuredAccountDB.verification_status == PENDING, live_pending),
            ),
        )
        count = await session.scalar(
            select(func.count()).select_from(InsuredAccountDB).where(or_(InsuredAccountDB.email == email, document_taken))
        )
        return bool(count)

    async def _decide(
        self, storage: DocumentStorage, verifier: IdentityVerifier, row: IdentityVerificationDB,
        account: InsuredAccountDB,
    ) -> VerificationResult:
        if row.provider_ref:
            return await self._verify(verifier, lambda: verifier.decide(row.provider_ref))
        keys = row.s3_keys or []
        if len(keys) < 2:
            return VerificationResult(PENDING)
        try:
            front, selfie = await storage.get_bytes(keys[0]), await storage.get_bytes(keys[1])
        except StorageUnavailableError:
            return VerificationResult(PENDING)
        return await self._verify(
            verifier,
            lambda: verifier.verify(account.document_type, account.document_number, account.full_name, front, selfie),
        )

    @staticmethod
    async def _verify(verifier: IdentityVerifier, call) -> VerificationResult:
        """Si el proveedor tarda más de 400 ms o falla, la verificación queda pendiente."""
        try:
            return await asyncio.wait_for(call(), VERIFY_TIMEOUT_SECONDS)
        except asyncio.TimeoutError:
            return VerificationResult(PENDING)
        except Exception as exc:
            logger.warning("El proveedor de identidad no respondió: %s", type(exc).__name__)
            return VerificationResult(PENDING)

    async def _fail(self, session: AsyncSession, subject: str, ip: Optional[str], result: str, now) -> None:
        await self._auth._audit(session, subject, None, ip, "register", result, now)
        await session.commit()

    async def _abort(
        self, session: AsyncSession, data: NewInsured, subject: str, ip: Optional[str], result: str, now
    ) -> None:
        """El rollback devolvería el código a la tabla: en una transacción aparte se consume y se registra el intento."""
        await session.rollback()
        await self._auth.consume_document_otp(
            session, data.document_number, PURPOSE_REGISTRATION, self._binding(data.document_type, data.phone)
        )
        await self._fail(session, subject, ip, result, now)

    @staticmethod
    async def _store(storage: DocumentStorage, prefix: str, photos: IdentityPhotos) -> List[str]:
        files = [("documento-frente", photos.document_front), ("selfie", photos.selfie)]
        if photos.document_back is not None:
            files.append(("documento-reverso", photos.document_back))
        keys = []
        for name, content in files:
            key = f"{prefix}{name}.jpg"
            await storage.put_bytes(key, content, "image/jpeg")
            keys.append(key)
        return keys

    @classmethod
    async def _purge(cls, storage: DocumentStorage, row: IdentityVerificationDB) -> int:
        """Todas las versiones bajo el prefijo de la verificación, aunque `s3_keys` no las liste."""
        return await storage.purge(cls._prefix(row.user_id, row.verification_id))

    @staticmethod
    async def _purge_quietly(storage: DocumentStorage, prefix: str) -> None:
        try:
            await storage.purge(prefix)
        except StorageUnavailableError:
            logger.warning("Quedaron imágenes de identidad sin borrar tras una falla del registro")

    @staticmethod
    def _prefix(user_id: str, verification_id: str) -> str:
        return f"{IDENTITY_PREFIX}/{user_id}/{verification_id}/"

    @staticmethod
    def _pseudonym(label: str, value: str) -> str:
        return hmac.new(
            settings.OTP_SECRET_KEY.get_secret_value().encode(), f"{label}:{value}".encode(), hashlib.sha256
        ).hexdigest()[:40]

    @classmethod
    def _subject(cls, document_type: str, document_number: str) -> str:
        """Seudónimo del documento para límites y auditoría: un HMAC, no el número."""
        return f"registro:{cls._pseudonym('registro', f'{document_type}:{document_number}')}"

    @classmethod
    def _phone_subject(cls, phone: str) -> str:
        """Seudónimo del teléfono para el límite de códigos de registro por número."""
        return f"registro:tel:{cls._pseudonym('registro-telefono', phone)}"

    @staticmethod
    def _binding(document_type: str, phone: str) -> str:
        return f"{document_type}:{phone}"


insured_registration_service = InsuredRegistrationService()
