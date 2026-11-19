"""Documentos que el asegurado agrega a su siniestro abierto desde la app (mejora FE-MOV-05): de 1 a 10 páginas JPEG
de hasta 5 MB, cada una con la huella que calculó el teléfono, registradas una sola vez por envío. Cada página se
decodifica y se vuelve a codificar en el servidor: lo que llega a S3 es una imagen limpia, sin EXIF ni comentarios."""
import asyncio
import hashlib
import io
import uuid
from datetime import datetime, timezone
from typing import Callable, Dict, List, Optional, Tuple

from PIL import Image, ImageOps
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
# Diez páginas de 5 MB, el manifiesto y el sobrecosto del multipart; el middleware corta antes de leer el formulario.
DOCUMENTS_MAX_BODY_BYTES = MAX_PAGES * MAX_PAGE_BYTES + 512 * 1024
# Una hoja carta escaneada a 300 ppp tiene unos 8,4 megapíxeles; más de 25 es una imagen hecha para agotar la memoria.
MAX_PAGE_PIXELS = 25_000_000
# Pillow avisa por encima de este tope y falla con DecompressionBombError por encima del doble, antes de decodificar.
Image.MAX_IMAGE_PIXELS = MAX_PAGE_PIXELS
# Una página decodificada ocupa hasta unos 150 MB y la tarea tiene 1 GB: el proceso recodifica dos envíos a la vez.
MAX_CONCURRENT_RECODES = 2
RECODE_SLOTS = asyncio.Semaphore(MAX_CONCURRENT_RECODES)
# Si la página limpia pasa de 5 MB se baja la calidad; si a 70 sigue pasando, se rechaza con 413.
RECODE_QUALITIES = (90, 80, 70)
CONTENT_TYPE = "image/jpeg"
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
    """La página falta, no es JPEG, no coincide con su huella o el siniestro ya no admite más."""

    def __init__(self, message: str):
        super().__init__(message)
        self.message = message


def clean_jpeg(content: bytes) -> Optional[bytes]:
    """La imagen decodificada y codificada de nuevo, con la orientación aplicada y sin EXIF, ICC ni comentarios;
    None si no es un JPEG que se pueda decodificar o si es más grande de lo que cabe en una hoja. Si ni a la menor
    calidad queda en 5 MB, lanza PageTooLargeError."""
    try:
        with Image.open(io.BytesIO(content), formats=["JPEG"]) as source:
            if source.width * source.height > MAX_PAGE_PIXELS:
                return None
            source.load()
            image = ImageOps.exif_transpose(source)
        if image.mode not in ("L", "RGB"):
            image = image.convert("RGB")
        image.info = {}
        for quality in RECODE_QUALITIES:
            out = io.BytesIO()
            image.save(out, "JPEG", quality=quality)
            if out.tell() <= MAX_PAGE_BYTES:
                return out.getvalue()
        raise PageTooLargeError()
    except (OSError, ValueError, SyntaxError, Image.DecompressionBombWarning, Image.DecompressionBombError):
        return None


def checked_pages(request: DocumentsUploadRequest, files: Dict[int, bytes]) -> Dict[int, bytes]:
    """Cada página declarada llega, pesa a lo sumo 5 MB, es JPEG por su firma, su SHA-256 es el del teléfono y se
    decodifica; devuelve las páginas limpias que se guardan. Solo JPEG: un PDF puede traer contenido activo (RISK-05)."""
    if set(files) != {p.position for p in request.pages}:
        raise DocumentRejectedError("Las páginas enviadas no coinciden con las declaradas.")
    cleaned = {}
    for page in request.pages:
        content = files[page.position]
        if len(content) > MAX_PAGE_BYTES:
            raise PageTooLargeError()
        if not content.startswith(JPEG_MAGIC):
            raise DocumentRejectedError(f"La página {page.position} no es una imagen JPEG.")
        if hashlib.sha256(content).hexdigest() != page.sha256:
            raise DocumentRejectedError(f"La página {page.position} cambió después de escanearla. Escanéela de nuevo.")
        clean = clean_jpeg(content)
        if clean is None:
            raise DocumentRejectedError(f"La página {page.position} no es una imagen JPEG válida.")
        cleaned[page.position] = clean
    return cleaned


async def recode_pages(request: DocumentsUploadRequest, files: Dict[int, bytes]) -> Dict[int, bytes]:
    """Revisa y recodifica las páginas fuera del ciclo de eventos, con a lo sumo dos envíos a la vez por proceso."""
    async with RECODE_SLOTS:
        return await asyncio.to_thread(checked_pages, request, files)


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
        # Otro envío con el mismo `upload_id` pudo terminar mientras este esperaba el bloqueo: se devuelve ese y S3
        # no se toca, para que lo guardado siga siendo lo que dice la huella registrada.
        existing = await self.already_received(session, document_number, claim_number, request.upload_id)
        if existing is not None:
            await session.rollback()
            return existing, False
        pages = await recode_pages(request, files)
        stored = await session.scalar(
            select(func.count()).select_from(ClaimDocumentDB).where(ClaimDocumentDB.claim_number == claim_number)
        )
        if stored + len(request.pages) > MAX_PAGES_PER_CLAIM:
            raise DocumentRejectedError(f"El siniestro ya tiene el máximo de {MAX_PAGES_PER_CLAIM} páginas de documentos.")
        uploaded_at = self.now()
        rows = []
        for page in request.pages:
            content = pages[page.position]
            key = f"siniestros/{claim_number}/documentos/{request.upload_id.lower()}/{page.position}.jpg"
            await storage.put_bytes(key, content, CONTENT_TYPE)
            # La huella guardada es la de lo que queda en S3: la imagen ya limpia; la del teléfono no se guarda (RISK-07).
            rows.append(ClaimDocumentDB(
                document_id=str(uuid.uuid4()), claim_number=claim_number, upload_id=request.upload_id.lower(),
                position=page.position, s3_key=key, sha256=hashlib.sha256(content).hexdigest(),
                content_type=CONTENT_TYPE, size_bytes=len(content), uploaded_at=uploaded_at,
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
