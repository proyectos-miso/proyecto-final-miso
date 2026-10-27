"""Datos de prueba de la búsqueda de pólizas (HU-WEB-08)."""
from datetime import datetime, timedelta, timezone

from sqlalchemy import insert

from app.core.security import create_access_token
from app.modules.auth.service import auth_service
from app.modules.policy.models import PolicyDB
from app.modules.rating.models import PartnerDB, QuoteDB
from app.modules.rating.service import rating_service

NOW = datetime(2026, 10, 27, 12, 0, tzinfo=timezone.utc)
ALIADO = "banco-aliado-1"
OTRO = "otro-socio-1"
REVOCADO = "socio-inactivo-1"


def tier(plan: str = "standard") -> dict:
    return {"tier": plan, "monthly_premium": 100000.0, "annual_premium": 1200000.0, "coverages": [], "breakdown": []}


def policy_row(number, document, partner_id=None, status="ACTIVE", holder=None, plan="standard", age_days=0) -> tuple:
    issued = NOW - timedelta(days=age_days)
    quote = {
        "quote_id": f"Q{number[-12:]}",
        "partner_id": partner_id,
        "document_number": document,
        "status": "active",
        "score_applied": 700.0,
        "tiers": [tier(plan)],
        "created_at": issued,
        "expires_at": issued + timedelta(days=15),
    }
    policy = {
        "policy_number": number,
        "quote_id": quote["quote_id"],
        "partner_id": partner_id,
        "insured_document": document,
        "beneficiaries": [],
        "conditions": tier(plan),
        "status": status,
        "issued_at": issued,
        "valid_until": issued + timedelta(days=365),
        "holder_name": holder,
        "holder_document_type": "CC" if holder else None,
        "holder_email": "titular@example.com" if holder else None,
        "holder_phone": "3000000000" if holder else None,
    }
    return quote, policy


async def add_policies(session, rows) -> None:
    quotes, policies = zip(*rows)
    await session.execute(insert(QuoteDB), list(quotes))
    await session.execute(insert(PolicyDB), list(policies))
    await session.commit()


async def seed_partners_and_users(session) -> None:
    """Socios semilla (activo y revocado), un tercer socio activo y un analista para cada caso."""
    await rating_service.seed_default_partners(session)
    session.add(
        PartnerDB(
            partner_id=OTRO,
            client_id="otro_socio",
            client_secret_hash="no-aplica",
            name="Otra Aseguradora Aliada",
            status="ACTIVE",
            quota_limit=10,
            quota_used=0,
        )
    )
    await session.commit()
    users = [
        ("operaciones@solventa.example", None),
        ("analista@bancoaliado.example", ALIADO),
        ("analista@revocado.example", REVOCADO),
    ]
    for username, partner_id in users:
        await auth_service.create_user(
            session,
            username=username,
            password="Prueba2026*x",
            full_name="Analista de Prueba",
            role="analyst",
            partner_id=partner_id,
        )


def headers(username: str, role: str = "analyst") -> dict:
    return {"Authorization": f"Bearer {create_access_token(subject=username, role=role)}"}
