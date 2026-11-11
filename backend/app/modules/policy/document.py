"""Carátula de la póliza en PDF, guardada en S3 y entregada con un enlace de 15 minutos (HU-WEB-16)."""
import hashlib
from datetime import datetime, timedelta, timezone
from typing import Callable, Optional

from fpdf import FPDF
from sqlalchemy import select, update
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.storage import DocumentStorage
from app.modules.policy.models import CaratulaLink, PolicyDB
from app.modules.policy.search import mask_document
from app.modules.policy.service import included_coverages, insured_amount
from app.modules.rating.models import PartnerDB

LINK_TTL = timedelta(minutes=15)
COLOMBIA = timezone(timedelta(hours=-5))
PLANS = {"basic": "Básico", "standard": "Estándar", "premium": "Premium"}


class PolicyCancelledError(Exception):
    pass


def caratula_key(policy_number: str, version: int = 1) -> str:
    """La clave sale del número ya validado por la ruta; el cliente nunca la manda.
    Cada endoso sube la versión y la carátula nueva no pisa la anterior (HU-WEB-15)."""
    return f"caratulas/{policy_number}.pdf" if version <= 1 else f"caratulas/{policy_number}-v{version}.pdf"


def cop(amount: float) -> str:
    """Como el portal: "$ 250.000.000 COP", para que el $ no se lea como dólares."""
    return "$ " + f"{round(amount):,}".replace(",", ".") + " COP"


def colombia(moment: datetime) -> datetime:
    return moment.astimezone(COLOMBIA)


def latin1(text: str) -> str:
    """Las fuentes base del PDF son Latin-1: lo que no cabe se cambia por "?" en vez de fallar."""
    return text.encode("latin-1", "replace").decode("latin-1")


def render_caratula(policy: PolicyDB, partner_name: Optional[str], generated_at: datetime) -> bytes:
    """Solo datos fijos desde la emisión (sin el estado, que cambia) y solo texto, sin imágenes, HTML ni enlaces;
    el titular va con el documento enmascarado y sin contacto."""
    conditions = policy.conditions
    included = included_coverages(conditions)
    pdf = FPDF(format="A4")
    pdf.set_title(f"Carátula de la póliza {policy.policy_number}")
    pdf.set_author("Solventa Seguros Digitales")
    pdf.add_page()

    def write(text: str, style: str = "", size: int = 10, height: float = 6) -> None:
        pdf.set_font("helvetica", style, size)
        pdf.multi_cell(0, height, latin1(text), new_x="LMARGIN", new_y="NEXT")

    def heading(text: str) -> None:
        pdf.ln(3)
        write(text, "B", 12, 8)

    # Mismas secciones y etiquetas que el detalle de Gestión (HU-WEB-09).
    write(f"Carátula de la póliza {policy.policy_number}", "B", 16, 9)
    version = getattr(policy, "version", None) or 1
    write("Solventa Seguros Digitales" + (f" · Versión {version} (con endosos)" if version > 1 else ""))

    heading("Titular")
    write(f"Nombre: {policy.holder_name or 'Sin nombre registrado'}")
    document_type = f"{policy.holder_document_type} " if policy.holder_document_type else ""
    write(f"Documento: {document_type}{mask_document(policy.insured_document)}")

    heading("Plan y vigencia")
    write(f"Plan: {PLANS.get(conditions['tier'], conditions['tier'])}")
    write(f"Vigencia: {colombia(policy.issued_at):%d/%m/%Y} - {colombia(policy.valid_until):%d/%m/%Y}")
    write(f"Socio: {partner_name or 'Portal Solventa'}")
    write(f"Suma asegurada: {cop(insured_amount(included))}")
    write(f"Prima mensual: {cop(conditions['monthly_premium'])}")
    write(f"Prima anual: {cop(conditions['annual_premium'])}")

    heading("Coberturas contratadas")
    for coverage in included:
        deductible = coverage.get("deductible", 0.0)
        write(f"{coverage['name']}: {cop(coverage['limit'])}" + (f", deducible {cop(deductible)}" if deductible else ""))

    heading("Constancia de Firma Electrónica")
    write("Documento suscrito con firma electrónica y verificación OTP de un solo uso.")
    write(f"Fecha de firma: {colombia(policy.issued_at):%d/%m/%Y a las %H:%M:%S} (hora de Colombia)")
    canonical_summary = f"POL:{policy.policy_number}|DOC:{policy.insured_document}|ISSUED:{policy.issued_at.isoformat()}|TIER:{conditions.get('tier')}"
    doc_hash = hashlib.sha256(canonical_summary.encode()).hexdigest()
    write(f"Hash criptográfico de validez probatoria (SHA-256): {doc_hash}")

    pdf.ln(6)
    write(
        f"Generada el {colombia(generated_at):%d/%m/%Y a las %H:%M} (hora de Colombia) con las condiciones "
        "contratadas al emitir la póliza.",
        "I",
        8,
        5,
    )
    return bytes(pdf.output())


async def get_caratula_link(
    session: AsyncSession,
    storage: DocumentStorage,
    policy_number: str,
    partner_id: Optional[str],
    now: Callable[[], datetime] = lambda: datetime.now(timezone.utc),
) -> Optional[CaratulaLink]:
    """None si no existe o es de otro socio; genera y guarda el PDF solo la primera vez."""
    stmt = (
        select(PolicyDB, PartnerDB.name)
        .outerjoin(PartnerDB, PartnerDB.partner_id == PolicyDB.partner_id)
        .where(PolicyDB.policy_number == policy_number)
    )
    if partner_id is not None:
        stmt = stmt.where(PolicyDB.partner_id == partner_id)
    row = (await session.execute(stmt)).first()
    if row is None:
        return None
    policy, partner_name = row
    if policy.status == "CANCELLED":
        raise PolicyCancelledError()

    key = policy.pdf_s3_key
    if key is None:
        key = caratula_key(policy.policy_number, getattr(policy, "version", None) or 1)
        await storage.put_pdf(key, render_caratula(policy, partner_name, now()))
        await session.execute(update(PolicyDB).where(PolicyDB.policy_number == policy.policy_number).values(pdf_s3_key=key))
        await session.commit()

    signed_at = now()
    url = await storage.download_url(key, f"caratula-{policy.policy_number}.pdf", int(LINK_TTL.total_seconds()))
    return CaratulaLink(url=url, expires_at=signed_at + LINK_TTL)
