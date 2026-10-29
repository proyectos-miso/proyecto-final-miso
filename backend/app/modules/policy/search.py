"""Búsqueda de pólizas para operaciones, con alcance por socio y documento enmascarado (HU-WEB-08)."""
from typing import Optional

from sqlalchemy import or_, select, text as sql
from sqlalchemy.ext.asyncio import AsyncConnection, AsyncSession

from app.modules.policy.models import PolicyDB, PolicySearchItem, PolicySearchResponse
from app.modules.rating.models import PartnerDB

SEARCH_LIMIT = 50

# Índices de trigramas para LIKE e ILIKE sobre número, documento y titular, y el orden por fecha de emisión.
SEARCH_INDEXES = (
    "CREATE EXTENSION IF NOT EXISTS pg_trgm",
    "CREATE INDEX IF NOT EXISTS ix_policies_policy_number_trgm ON policies USING gin (policy_number gin_trgm_ops)",
    "CREATE INDEX IF NOT EXISTS ix_policies_insured_document_trgm ON policies USING gin (insured_document gin_trgm_ops)",
    "CREATE INDEX IF NOT EXISTS ix_policies_holder_name_trgm ON policies USING gin (holder_name gin_trgm_ops)",
    "CREATE INDEX IF NOT EXISTS ix_policies_issued_at ON policies (issued_at DESC, policy_number DESC)",
)


async def create_search_indexes(conn: AsyncConnection) -> None:
    for statement in SEARCH_INDEXES:
        await conn.execute(sql(statement))


def escape_like(text: str) -> str:
    """El texto del analista se busca literal: sin comodines propios de LIKE."""
    return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")


def mask_document(document: str) -> str:
    visible = document[-4:] if len(document) > 4 else ""
    return "*" * (len(document) - len(visible)) + visible


async def search_policies(
    session: AsyncSession,
    query: str,
    status: Optional[str],
    partner_id: Optional[str],
    limit: int = SEARCH_LIMIT,
) -> PolicySearchResponse:
    """Número y documento por prefijo; socio y titular por contenido. Un usuario de socio solo ve las de su socio."""
    stmt = select(PolicyDB, PartnerDB.name).outerjoin(PartnerDB, PartnerDB.partner_id == PolicyDB.partner_id)
    if partner_id is not None:
        stmt = stmt.where(PolicyDB.partner_id == partner_id)
    if status:
        stmt = stmt.where(PolicyDB.status == status)
    text = query.strip()
    if text:
        like = escape_like(text)
        stmt = stmt.where(
            or_(
                PolicyDB.policy_number.ilike(f"{like}%", escape="\\"),
                PolicyDB.insured_document.like(f"{like}%", escape="\\"),
                PolicyDB.partner_id.in_(
                    select(PartnerDB.partner_id).where(PartnerDB.name.ilike(f"%{like}%", escape="\\"))
                ),
                PolicyDB.holder_name.ilike(f"%{like}%", escape="\\"),
            )
        )
    stmt = stmt.order_by(PolicyDB.issued_at.desc(), PolicyDB.policy_number.desc()).limit(limit + 1)
    rows = (await session.execute(stmt)).all()
    items = [
        PolicySearchItem(
            policy_number=policy.policy_number,
            holder_name=policy.holder_name,
            holder_document=mask_document(policy.insured_document),
            plan=policy.conditions["tier"],
            status=policy.status,
            valid_from=policy.issued_at,
            valid_until=policy.valid_until,
            partner_name=partner_name,
        )
        for policy, partner_name in rows[:limit]
    ]
    return PolicySearchResponse(items=items, has_more=len(rows) > limit)
