"""Gestión de socios B2B desde el portal de Solventa: listado, revocación, rotación y aprobación (FE-WEB-05)."""
import hashlib
import secrets
import string
import unicodedata
from datetime import datetime
from typing import List, Optional

from pydantic import BaseModel
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.security import get_password_hash, log_partner_security_event
from app.modules.rating.models import PartnerDB, PartnerStatus

SANDBOX = "sandbox"
PRODUCTION = "production"
SANDBOX_QUOTA = 1000
LIST_LIMIT = 200
REVOKED_OR_MISSING = "Socio revocado o inexistente"
SANDBOX_CANNOT_ISSUE = "Un socio en sandbox no emite pólizas reales"
SLUG_CHARS = set(string.ascii_lowercase + string.digits)


class PartnerNotFoundError(Exception):
    pass


class PartnerRevokedError(Exception):
    pass


class PartnerBlockedError(Exception):
    """El socio no puede emitir ni cobrar; `detail` es el mensaje genérico para el cliente."""

    def __init__(self, detail: str):
        super().__init__(detail)
        self.detail = detail


class PartnerSummary(BaseModel):
    partner_id: str
    name: str
    environment: str
    status: str
    quota_limit: int
    quota_used: int
    created_at: Optional[datetime]
    has_contact: bool
    contact_email_masked: Optional[str]


class PartnerPage(BaseModel):
    items: List[PartnerSummary]
    truncated: bool


class PartnerCredentials(BaseModel):
    partner_id: str
    client_id: str
    client_secret: str
    message: str


class RegisteredPartner(BaseModel):
    partner_id: str
    name: str
    client_id: str
    client_secret: str
    environment: str
    quota_limit: int


def new_client_secret() -> str:
    return f"sec_{secrets.token_urlsafe(24)}"


def slug_of(name: str) -> str:
    """Solo [a-z0-9]: «Compañía Bolívar» da `companiabolivar`, para que el id pase la validación de las rutas."""
    ascii_name = unicodedata.normalize("NFKD", name).encode("ascii", "ignore").decode("ascii").lower()
    return "".join(c for c in ascii_name if c in SLUG_CHARS)[:20] or "socio"


def operator_ref(username: str) -> str:
    """Referencia estable del operador para la auditoría, sin su correo."""
    return hashlib.sha256(username.encode("utf-8")).hexdigest()[:12]


def environment_of(partner: PartnerDB) -> str:
    """Las filas viejas pueden traer el entorno en mayúsculas; un valor vacío es producción, el default del modelo."""
    return (partner.environment or PRODUCTION).lower()


def is_sandbox(partner: PartnerDB) -> bool:
    return environment_of(partner) == SANDBOX


def mask_email(email: Optional[str]) -> Optional[str]:
    if not email or "@" not in email:
        return None
    local, domain = email.rsplit("@", 1)
    return f"{local[:1]}***@{domain}"


def summary(partner: PartnerDB) -> PartnerSummary:
    return PartnerSummary(
        partner_id=partner.partner_id,
        name=partner.name,
        environment=environment_of(partner),
        status=partner.status,
        quota_limit=partner.quota_limit,
        quota_used=partner.quota_used or 0,
        created_at=partner.created_at,
        has_contact=bool(partner.contact_email),
        contact_email_masked=mask_email(partner.contact_email),
    )


async def ensure_partner_can_issue(session: AsyncSession, partner_id: Optional[str]) -> None:
    """Regla única para emitir o cobrar en nombre de un socio: debe existir, estar activo y en producción."""
    if partner_id is None:
        return
    partner = await session.get(PartnerDB, partner_id)
    if partner is None or partner.status != PartnerStatus.ACTIVE.value:
        log_partner_security_event(
            event_code="PARTNER_ISSUE_BLOCKED",
            reason="Emisión o cobro de un socio revocado o inexistente",
            partner_id=partner_id,
        )
        raise PartnerBlockedError(REVOKED_OR_MISSING)
    if is_sandbox(partner):
        log_partner_security_event(
            event_code="PARTNER_SANDBOX_ISSUE_BLOCKED",
            reason="Emisión o cobro de un socio en sandbox",
            client_id=partner.client_id,
            partner_id=partner.partner_id,
        )
        raise PartnerBlockedError(SANDBOX_CANNOT_ISSUE)


def _audit(partner: PartnerDB, event_code: str, reason: str, endpoint: str, operator: str) -> None:
    log_partner_security_event(
        event_code=event_code,
        reason=reason,
        client_id=partner.client_id,
        partner_id=partner.partner_id,
        endpoint=endpoint,
        operator_ref=operator_ref(operator),
    )


async def register(session: AsyncSession, name: str, contact_email: str, operator: str) -> RegisteredPartner:
    """Alta en sandbox con cuota de prueba; el secreto se entrega una sola vez y solo se guarda su hash."""
    slug = slug_of(name)
    client_secret = new_client_secret()
    partner = PartnerDB(
        partner_id=f"{slug}_{secrets.token_hex(4)}",
        name=name,
        contact_email=contact_email,
        client_id=f"client_{slug}_{secrets.token_hex(4)}",
        client_secret_hash=get_password_hash(client_secret),
        status=PartnerStatus.ACTIVE.value,
        quota_limit=SANDBOX_QUOTA,
        quota_used=0,
        quota_window_seconds=3600,
        environment=SANDBOX,
    )
    session.add(partner)
    await session.commit()
    _audit(partner, "PARTNER_REGISTERED_BY_OPERATOR", "Un operador de Solventa registró al socio en sandbox",
           "/api/v1/quote-edge/partners/register", operator)
    return RegisteredPartner(
        partner_id=partner.partner_id,
        name=partner.name,
        client_id=partner.client_id,
        client_secret=client_secret,
        environment=SANDBOX,
        quota_limit=SANDBOX_QUOTA,
    )


async def list_partners(session: AsyncSession) -> PartnerPage:
    """Los más recientes primero; pide uno de más para saber si el listado quedó truncado."""
    limit = LIST_LIMIT
    rows = list(await session.scalars(
        select(PartnerDB).order_by(PartnerDB.created_at.desc(), PartnerDB.partner_id).limit(limit + 1)
    ))
    return PartnerPage(items=[summary(partner) for partner in rows[:limit]], truncated=len(rows) > limit)


async def _locked(session: AsyncSession, partner_id: str) -> PartnerDB:
    partner = await session.get(PartnerDB, partner_id, with_for_update=True)
    if partner is None:
        raise PartnerNotFoundError()
    return partner


async def revoke(session: AsyncSession, partner_id: str, operator: str) -> PartnerSummary:
    """Idempotente; los tokens ya emitidos dejan de servir porque cada pedido vuelve a leer el estado."""
    partner = await _locked(session, partner_id)
    if partner.status == PartnerStatus.REVOKED.value:
        result = summary(partner)
        await session.rollback()
        return result
    partner.status = PartnerStatus.REVOKED.value
    await session.commit()
    _audit(partner, "PARTNER_REVOKED_BY_OPERATOR", "Un operador de Solventa revocó al socio",
           "/api/v1/experience/partners/{partner_id}/revoke", operator)
    return summary(partner)


async def rotate_secret(session: AsyncSession, partner_id: str, operator: str) -> PartnerCredentials:
    """Genera un secreto nuevo y guarda solo su hash; el anterior ya no sirve para pedir tokens (RISK-04)."""
    partner = await _locked(session, partner_id)
    if partner.status == PartnerStatus.REVOKED.value:
        await session.rollback()
        raise PartnerRevokedError()
    client_secret = new_client_secret()
    partner.client_secret_hash = get_password_hash(client_secret)
    await session.commit()
    _audit(partner, "PARTNER_CREDENTIALS_ROTATED", "Un operador de Solventa rotó el secreto del socio",
           "/api/v1/experience/partners/{partner_id}/credentials", operator)
    return PartnerCredentials(
        partner_id=partner.partner_id,
        client_id=partner.client_id,
        client_secret=client_secret,
        message="Guarde el secreto ahora: no se volverá a mostrar y el anterior ya no sirve para pedir tokens.",
    )


async def approve(session: AsyncSession, partner_id: str, quota_limit: int, operator: str) -> PartnerDB:
    """Promueve a producción; un socio revocado no se reactiva por esta vía."""
    partner = await _locked(session, partner_id)
    if partner.status == PartnerStatus.REVOKED.value:
        await session.rollback()
        raise PartnerRevokedError()
    partner.environment = PRODUCTION
    partner.quota_limit = quota_limit
    await session.commit()
    _audit(partner, "PARTNER_APPROVED_BY_OPERATOR", "Un operador de Solventa promovió al socio a producción",
           "/api/v1/experience/partners/{partner_id}/approve", operator)
    return partner
