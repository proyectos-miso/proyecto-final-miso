"""Datos de prueba de la búsqueda y del detalle de pólizas (HU-WEB-08, HU-WEB-09)."""
from datetime import datetime, timedelta, timezone

from sqlalchemy import insert

from app.core.security import create_access_token, get_password_hash
from app.modules.auth.service import DEFAULT_SEED_TOTP_SECRET, auth_service
from app.modules.claims.models import ClaimDB
from app.modules.payments.models import PaymentTransactionDB
from app.modules.policy.models import PolicyDB
from app.modules.rating.models import PartnerDB, QuoteDB
from app.modules.rating.service import rating_service

NOW = datetime(2026, 10, 27, 12, 0, tzinfo=timezone.utc)
ALIADO = "banco-aliado-1"
OTRO = "otro-socio-1"
REVOCADO = "socio-inactivo-1"


COVERAGES = [
    {"id": "death", "name": "Muerte por cualquier causa", "limit": 200000000.0, "deductible": 0.0, "included": True},
    {"id": "disability", "name": "Incapacidad total y permanente", "limit": 200000000.0, "deductible": 0.0, "included": True},
    {"id": "critical_illness", "name": "Enfermedades graves", "limit": 100000000.0, "deductible": 0.0, "included": False},
]


def tier(plan: str = "standard") -> dict:
    return {"tier": plan, "monthly_premium": 100000.0, "annual_premium": 1200000.0, "coverages": COVERAGES, "breakdown": []}


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
    hashed = get_password_hash("Prueba2026*x")
    for username, partner_id in users:
        await auth_service._add_user(session, username, hashed, "Analista de Prueba", "analyst", DEFAULT_SEED_TOTP_SECRET, partner_id)


def payment(policy_number, number, status="APPROVED", minutes=0, reason=None) -> PaymentTransactionDB:
    return PaymentTransactionDB(
        transaction_id=f"00000000-0000-0000-0000-{number:012d}",
        partner_id="portal",
        idempotency_key=f"pago-{policy_number}-{number}",
        policy_number=policy_number,
        amount=100000.0,
        currency="COP",
        payment_method="PSE",
        status=status,
        decline_reason=reason,
        receipt_number=f"REC-2026-{number:05d}" if status == "APPROVED" else None,
        created_at=NOW + timedelta(minutes=minutes),
    )


def claim(policy_number, number, status="REPORTED", days=0) -> ClaimDB:
    return ClaimDB(claim_number=f"SIN-2026-{number:05d}", policy_number=policy_number, status=status,
                   reported_at=NOW + timedelta(days=days))


def headers(username: str, role: str = "analyst") -> dict:
    return {"Authorization": f"Bearer {create_access_token(subject=username, role=role)}"}
