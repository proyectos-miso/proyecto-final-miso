"""Documentos que el asegurado agrega a su siniestro abierto desde la app (mejora FE-MOV-05): de 1 a 10 páginas JPEG
o PDF de hasta 5 MB, cada una con la huella que calculó el teléfono, registradas una sola vez por envío."""
import hashlib
import uuid
from datetime import datetime, timezone
from typing import Callable, Dict, List, Optional, Tuple

from pydantic import BaseModel, ConfigDict, Field, field_validator
from sqlalchemy import func, select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.outbox import OutboxMessageDB
from app.core.storage import DocumentStorage
from app.modules.claims.intake import JPEG_MAGIC
from app.modules.claims.models import OPEN_STATUSES, ClaimDB, ClaimDocumentDB
from app.modules.claims.service import ClaimClosedError, ClaimNotFoundError, claim_service
from app.modules.policy.models import PolicyDB

MAX_PAGES = 10
MAX_PAGE_BYTES = 5_000_000
MAX_PAGES_PER_CLAIM = 50
PDF_MAGIC = b"%PDF-"
FORMATS = ((JPEG_MAGIC, "image/jpeg", "jpg"), (PDF_MAGIC, "application/pdf", "pdf"))
UPLOADED_BY = "App del asegurado"


class DocumentPage(BaseModel):
    model_config = ConfigDict(extra="forbid")

    position: int = Field(..., ge=1, le=MAX_PAGES)
    sha256: str = Field(..., pattern=r"^[0-9a-f]{64}$")


class DocumentsUploadRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    upload_id: str = Field(..., pattern=r"^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
    pages: List[DocumentPage] = Field(..., min_length=1, max_length=MAX_PAGES)

    @field_validator("pages")
    @classmethod
    def consecutive_positions(cls, pages: List[DocumentPage]) -> List[DocumentPage]:
        if sorted(p.position for p in pages) != list(range(1, len(pages) + 1)):
            raise ValueError("Las páginas deben ir numeradas desde 1 sin repetir.")
        return pages


class DocumentsReceipt(BaseModel):
    claim_number: str
    upload_id: str
    pages: int
    uploaded_at: datetime


class PageTooLargeError(Exception):
    pass


class DocumentRejectedError(Exception):
    """La página falta, no es JPEG ni PDF, no coincide con su huella o el siniestro ya no admite más."""

    def __init__(self, message: str):
        super().__init__(message)
        self.message = message


def page_format(content: bytes) -> Optional[Tuple[str, str]]:
    """Tipo y extensión según la firma del archivo, no según lo que declare el cliente."""
    return next(((kind, ext) for magic, kind, ext in FORMATS if content.startswith(magic)), None)


def checked_pages(request: DocumentsUploadRequest, files: Dict[int, bytes]) -> Dict[int, Tuple[str, str]]:
    """Cada página declarada llega, pesa a lo sumo 5 MB, es JPEG o PDF y su SHA-256 es el del teléfono."""
    if set(files) != {p.position for p in request.pages}:
        raise DocumentRejectedError("Las páginas enviadas no coinciden con las declaradas.")
    formats = {}
    for page in request.pages:
        content = files[page.position]
        if len(content) > MAX_PAGE_BYTES:
            raise PageTooLargeError()
        detected = page_format(content)
        if detected is None:
            raise DocumentRejectedError(f"La página {page.position} no es una imagen JPEG ni un PDF.")
        if hashlib.sha256(content).hexdigest() != page.sha256:
            raise DocumentRejectedError(f"La página {page.position} cambió después de escanearla. Escanéela de nuevo.")
        formats[page.position] = detected
    return formats


class ClaimDocuments:
    def __init__(self, now: Callable[[], datetime] = lambda: datetime.now(timezone.utc)):
        self.now = now

    async def receive(
        self, session: AsyncSession, storage: DocumentStorage, document_number: str, claim_number: str,
        request: DocumentsUploadRequest, files: Dict[int, bytes],
    ) -> Tuple[DocumentsReceipt, bool]:
        """Devuelve la constancia y si el envío es nuevo; el mismo `upload_id` otra vez devuelve el ya registrado."""
        existing = await self.already_received(session, document_number, claim_number, request.upload_id)
        if existing is not None:
            return existing, False
        claim = await self.own_open_claim(session, document_number, claim_number)
        formats = checked_pages(request, files)
        stored = await session.scalar(
            select(func.count()).select_from(ClaimDocumentDB).where(ClaimDocumentDB.claim_number == claim_number)
        )
        if stored + len(request.pages) > MAX_PAGES_PER_CLAIM:
            raise DocumentRejectedError(f"El siniestro ya tiene el máximo de {MAX_PAGES_PER_CLAIM} páginas de documentos.")
        uploaded_at = self.now()
        rows = []
        for page in request.pages:
            content_type, ext = formats[page.position]
            key = f"siniestros/{claim_number}/documentos/{request.upload_id.lower()}/{page.position}.{ext}"
            await storage.put_bytes(key, files[page.position], content_type)
            rows.append(ClaimDocumentDB(
                document_id=str(uuid.uuid4()), claim_number=claim_number, upload_id=request.upload_id.lower(),
                position=page.position, s3_key=key, sha256=page.sha256, content_type=content_type,
                size_bytes=len(files[page.position]), uploaded_at=uploaded_at,
            ))
        session.add_all(rows)
        detail = {"upload_id": request.upload_id.lower(), "pages": len(rows)}
        claim_service.event(session, claim, "DOCUMENTS_ADDED", UPLOADED_BY, detail=detail)
        # Solo identificadores: ni contenido, ni huellas, ni datos del asegurado (ASR-05).
        session.add(OutboxMessageDB(aggregate_id=claim_number, event_type="ClaimDocumentsAdded",
                                    payload={"claim_number": claim_number, **detail}))
        try:
            await session.commit()
        except IntegrityError:
            # Dos envíos simultáneos con el mismo `upload_id`: gana el primero y este devuelve su constancia.
            await session.rollback()
            existing = await self.already_received(session, document_number, claim_number, request.upload_id)
            if existing is None:
                raise
            return existing, False
        return DocumentsReceipt(claim_number=claim_number, upload_id=request.upload_id.lower(), pages=len(rows),
                                uploaded_at=uploaded_at), True

    @staticmethod
    async def already_received(
        session: AsyncSession, document_number: str, claim_number: str, upload_id: str
    ) -> Optional[DocumentsReceipt]:
        rows = list(await session.scalars(
            select(ClaimDocumentDB).where(ClaimDocumentDB.upload_id == upload_id.lower())
        ))
        if not rows:
            return None
        owner = await session.scalar(
            select(PolicyDB.insured_document)
            .join(ClaimDB, ClaimDB.policy_number == PolicyDB.policy_number)
            .where(ClaimDB.claim_number == rows[0].claim_number)
        )
        if owner != document_number:
            raise ClaimNotFoundError()
        if rows[0].claim_number != claim_number:
            raise DocumentRejectedError("Ese envío ya se registró en otro siniestro.")
        return DocumentsReceipt(claim_number=claim_number, upload_id=rows[0].upload_id, pages=len(rows),
                                uploaded_at=rows[0].uploaded_at)

    @staticmethod
    async def own_open_claim(session: AsyncSession, document_number: str, claim_number: str) -> ClaimDB:
        """El siniestro del titular, bloqueado mientras se agregan las páginas; uno ajeno responde como inexistente."""
        claim = await session.scalar(
            select(ClaimDB)
            .join(PolicyDB, PolicyDB.policy_number == ClaimDB.policy_number)
            .where(ClaimDB.claim_number == claim_number, PolicyDB.insured_document == document_number)
            .with_for_update(of=ClaimDB)
        )
        if claim is None:
            raise ClaimNotFoundError()
        if claim.status not in OPEN_STATUSES:
            raise ClaimClosedError()
        return claim


claim_documents = ClaimDocuments()
