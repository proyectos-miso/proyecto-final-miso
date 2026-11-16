"""Registro del asegurado desde la app con foto del documento y selfie (mejora FE-MOV-01).

Las imágenes solo se guardan mientras la verificación está pendiente; con la decisión se borran.
Los límites y la auditoría usan un seudónimo del documento, nunca el número en claro.
"""
import asyncio
import hashlib
import hmac
import logging
import uuid
from dataclasses import dataclass
from datetime import timedelta
from typing import List, Optional

from sqlalchemy import func, or_, select, text, update
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
REVOKED = "REVOKED"

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
        await self._auth._check_ip(session, ip, now, otp_request=True)
        requested = await self._auth._attempt_times(
            session, LoginAttemptDB.email == subject, "otp_request", ("success",), now - timedelta(hours=1)
        )
        retry_after = policy.otp_request_retry_after(requested, now)
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

        valid = await self._auth.verify_document_otp(
            session, data.document_number, PURPOSE_REGISTRATION, data.otp_code, data.document_type,
            commit=False, binding=self._binding(data.document_type, data.phone),
        )
        if not valid:
            await self._fail(session, subject, ip, "bad_code", now)
            raise InvalidRegistrationCodeError()

        # El documento no tiene índice único: el bloqueo serializa dos registros con el mismo número.
        await session.execute(
            text("SELECT pg_advisory_xact_lock(hashtext(:key))"), {"key": f"registro:{data.document_number}"}
        )
        taken = await session.scalar(
            select(func.count()).select_from(InsuredAccountDB).where(
                or_(InsuredAccountDB.email == email, InsuredAccountDB.document_number == data.document_number)
            )
        )
        if taken:
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
            await session.rollback()
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
        profile_service.add_biometric_consent(session, data.document_type, data.document_number, now)

        keys: List[str] = []
        if result.status == PENDING:
            try:
                await self._store(storage, account.user_id, verification.verification_id, photos, keys)
            except StorageUnavailableError:
                await session.rollback()
                await self._delete_quietly(storage, keys)
                raise
            verification.s3_keys = keys
        await self._auth._audit(session, subject, account.user_id, ip, "register", "success", now)
        tokens = self._auth._issue_tokens(account)
        try:
            await session.commit()
        except IntegrityError:
            await session.rollback()
            await self._delete_quietly(storage, keys)
            raise DuplicateAccountError()
        logger.info("Registro desde la app con verificación %s", result.status)
        return RegistrationOutcome(result.status, tokens)

    async def decide_pending(self, session: AsyncSession, storage: DocumentStorage, verifier: IdentityVerifier) -> int:
        """Verificación diferida: pide la decisión de cada pendiente y borra sus imágenes. Devuelve cuántas decidió."""
        rows = (
            await session.execute(
                select(IdentityVerificationDB)
                .where(IdentityVerificationDB.status == PENDING)
                .with_for_update(skip_locked=True)
            )
        ).scalars().all()
        decided = 0
        for row in rows:
            account = await session.get(InsuredAccountDB, row.user_id)
            result = await self._decide(storage, verifier, row, account)
            if result.status == PENDING:
                continue
            try:
                await self._delete(storage, row.s3_keys or [])
            except StorageUnavailableError:
                continue
            now = self._auth._clock()
            row.status, row.decided_at, row.s3_keys = result.status, now, None
            row.provider_ref = result.reference or row.provider_ref
            account.verification_status = result.status
            if result.status == APPROVED:
                account.verified_at = now
            else:
                # Rechazada después del registro: se cierran sus sesiones y sus huellas.
                account.token_version += 1
                await session.execute(
                    update(InsuredDeviceDB)
                    .where(InsuredDeviceDB.user_id == account.user_id, InsuredDeviceDB.revoked_at.is_(None))
                    .values(revoked_at=now)
                )
            decided += 1
        await session.commit()
        if decided:
            logger.info("Verificación diferida: %s decididas", decided)
        return decided

    async def revoke_biometric_consent(
        self, session: AsyncSession, storage: DocumentStorage, account: InsuredAccountDB
    ) -> int:
        """Revoca la autorización: borra las imágenes pendientes y detiene su verificación. Devuelve cuántas borró."""
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
                await self._delete(storage, row.s3_keys or [])
                deleted += len(row.s3_keys or [])
                row.status, row.s3_keys, row.decided_at = REVOKED, None, self._auth._clock()
        except StorageUnavailableError:
            await session.rollback()
            raise
        await profile_service.revoke_biometric_consent(session, account.document_number)
        await session.commit()
        return deleted

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

    @staticmethod
    async def _store(
        storage: DocumentStorage, user_id: str, verification_id: str, photos: IdentityPhotos, keys: List[str]
    ) -> None:
        """Agrega a `keys` cada imagen guardada, para poder borrarlas si una falla."""
        prefix = f"{IDENTITY_PREFIX}/{user_id}/{verification_id}"
        files = [("documento-frente", photos.document_front), ("selfie", photos.selfie)]
        if photos.document_back is not None:
            files.append(("documento-reverso", photos.document_back))
        for name, content in files:
            key = f"{prefix}/{name}.jpg"
            await storage.put_bytes(key, content, "image/jpeg")
            keys.append(key)

    @staticmethod
    async def _delete(storage: DocumentStorage, keys: List[str]) -> None:
        for key in keys:
            await storage.delete(key)

    @classmethod
    async def _delete_quietly(cls, storage: DocumentStorage, keys: List[str]) -> None:
        try:
            await cls._delete(storage, keys)
        except StorageUnavailableError:
            logger.warning("Quedaron imágenes de identidad sin borrar tras una falla del registro")

    @staticmethod
    def _subject(document_type: str, document_number: str) -> str:
        """Seudónimo del documento para límites y auditoría: un HMAC, no el número."""
        digest = hmac.new(
            settings.OTP_SECRET_KEY.get_secret_value().encode(),
            f"registro:{document_type}:{document_number}".encode(),
            hashlib.sha256,
        ).hexdigest()
        return f"registro:{digest[:40]}"

    @staticmethod
    def _binding(document_type: str, phone: str) -> str:
        return f"{document_type}:{phone}"


insured_registration_service = InsuredRegistrationService()
