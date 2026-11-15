"""Recepción del reporte del asegurado desde la app (HU-MOV-09): una vez por reporte, con la huella de cada foto
verificada antes de guardarla y el radicado asignado al registrar (HU-MOV-10)."""
import hashlib
import uuid
from datetime import datetime, timezone
from typing import Callable, Dict, List, Literal, Optional, Tuple

from pydantic import BaseModel, Field, field_validator
from sqlalchemy import select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.storage import DocumentStorage
from app.modules.claims.models import CLAIM_NUMBER_SEQ, ClaimDB
from app.modules.claims.service import NewPhoto, claim_service
from app.modules.policy.models import PolicyDB

MAX_PHOTOS = 5
MAX_PHOTO_BYTES = 2_000_000
MAX_DESCRIPTION = 500
JPEG_MAGIC = b"\xff\xd8\xff"


class ReportPhoto(BaseModel):
    position: int = Field(..., ge=1, le=MAX_PHOTOS)
    sha256: str = Field(..., pattern=r"^[0-9a-f]{64}$")
    captured_at: datetime
    trusted_time_source: Optional[Literal["GNSS", "NETWORK"]] = None
    latitude: float = Field(..., ge=-90, le=90)
    longitude: float = Field(..., ge=-180, le=180)
    accuracy_m: float = Field(..., ge=0)
    mock_location: bool


class ClaimReportRequest(BaseModel):
    report_id: str = Field(..., pattern=r"^[0-9a-fA-F-]{36}$")
    policy_number: str = Field(..., pattern=r"^POL-\d{4}-\d{5}$")
    description: str = Field(..., min_length=1, max_length=MAX_DESCRIPTION)
    photos: List[ReportPhoto] = Field(..., min_length=1, max_length=MAX_PHOTOS)

    @field_validator("photos")
    @classmethod
    def consecutive_positions(cls, photos: List[ReportPhoto]) -> List[ReportPhoto]:
        if sorted(p.position for p in photos) != list(range(1, len(photos) + 1)):
            raise ValueError("Las fotos deben ir numeradas desde 1 sin repetir.")
        return photos


class ClaimReceipt(BaseModel):
    claim_number: str
    status: str
    reported_at: datetime


class PolicyNotAvailableError(Exception):
    """La póliza no es del asegurado o no está vigente: responde igual que si no existiera."""


class PhotoTooLargeError(Exception):
    pass


class PhotoRejectedError(Exception):
    """La foto falta, no es JPEG o no coincide con la huella del teléfono."""

    def __init__(self, message: str):
        super().__init__(message)
        self.message = message


async def next_claim_number(session: AsyncSession, moment: datetime) -> str:
    """Radicado SIN-AAAA-NNNNN de la secuencia: único aunque dos siniestros se registren a la vez (HU-MOV-10)."""
    sequence = await session.scalar(select(CLAIM_NUMBER_SEQ.next_value()))
    return f"SIN-{moment.year}-{sequence:05d}"


def receipt(claim: ClaimDB) -> ClaimReceipt:
    return ClaimReceipt(claim_number=claim.claim_number, status=claim.status, reported_at=claim.reported_at)


def checked_photos(report: ClaimReportRequest, files: Dict[int, bytes]) -> Dict[int, bytes]:
    """Cada foto declarada llega, es un JPEG de máximo 2 MB y su SHA-256 es el que calculó el teléfono (RC-11)."""
    if set(files) != {p.position for p in report.photos}:
        raise PhotoRejectedError("Las fotos enviadas no coinciden con las del reporte.")
    for photo in report.photos:
        content = files[photo.position]
        if len(content) > MAX_PHOTO_BYTES:
            raise PhotoTooLargeError()
        if not content.startswith(JPEG_MAGIC):
            raise PhotoRejectedError(f"La foto {photo.position} no es una imagen JPEG.")
        if hashlib.sha256(content).hexdigest() != photo.sha256:
            raise PhotoRejectedError(
                f"La foto {photo.position} cambió después de tomarla. Descarte el reporte y tome las fotos de nuevo."
            )
    return files


class ClaimIntake:
    def __init__(self, now: Callable[[], datetime] = lambda: datetime.now(timezone.utc)):
        self.now = now

    async def receive(
        self, session: AsyncSession, storage: DocumentStorage, document_number: str,
        report: ClaimReportRequest, files: Dict[int, bytes],
    ) -> Tuple[ClaimReceipt, bool]:
        """Devuelve el siniestro y si es nuevo; el mismo reporte enviado otra vez devuelve el que ya existe (AC-03)."""
        existing = await self.already_received(session, document_number, report.report_id)
        if existing is not None:
            return receipt(existing), False
        policy = await self.active_policy(session, document_number, report.policy_number)
        checked_photos(report, files)
        reported_at = self.now()
        claim_number = await next_claim_number(session, reported_at)
        photos = []
        for photo in report.photos:
            key = f"siniestros/{claim_number}/{photo.position}.jpg"
            await storage.put_bytes(key, files[photo.position], "image/jpeg")
            photos.append(NewPhoto(
                photo_id=str(uuid.uuid4()), position=photo.position, s3_key=key, sha256=photo.sha256,
                latitude=photo.latitude, longitude=photo.longitude, accuracy_m=photo.accuracy_m,
                mock_location=photo.mock_location, captured_at=photo.captured_at,
                trusted_time_source=photo.trusted_time_source,
            ))
        try:
            claim = await claim_service.register_claim(
                session, claim_number, policy.policy_number, reported_at, description=report.description,
                photos=photos, client_report_id=report.report_id,
            )
        except IntegrityError:
            # Dos envíos simultáneos del mismo reporte: gana el primero y este devuelve ese siniestro.
            await session.rollback()
            existing = await self.already_received(session, document_number, report.report_id)
            if existing is None:
                raise
            return receipt(existing), False
        return receipt(claim), True

    @staticmethod
    async def already_received(session: AsyncSession, document_number: str, report_id: str) -> Optional[ClaimDB]:
        claim = await session.scalar(select(ClaimDB).where(ClaimDB.client_report_id == report_id))
        if claim is None:
            return None
        owner = await session.scalar(
            select(PolicyDB.insured_document).where(PolicyDB.policy_number == claim.policy_number)
        )
        if owner != document_number:
            raise PolicyNotAvailableError()
        return claim

    async def active_policy(self, session: AsyncSession, document_number: str, policy_number: str) -> PolicyDB:
        policy = await session.scalar(select(PolicyDB).where(
            PolicyDB.policy_number == policy_number,
            PolicyDB.insured_document == document_number,
            PolicyDB.status == "ACTIVE",
            PolicyDB.valid_until > self.now(),
        ))
        if policy is None:
            raise PolicyNotAvailableError()
        return policy


claim_intake = ClaimIntake()
