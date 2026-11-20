"""Prestadores y solicitudes de asistencia del asegurado (mejora FE-MOV-07).

El catálogo ficticio de prestadores lo siembra el arranque fuera de producción, después de las ciudades.

Revision ID: 0009
Revises: 0008
Create Date: 2026-11-15
"""
from alembic import op

revision = "0009"
down_revision = "0008"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.execute("""
        CREATE TABLE IF NOT EXISTS providers (
            provider_id VARCHAR(16) PRIMARY KEY,
            name VARCHAR(120) NOT NULL,
            kind VARCHAR(20) NOT NULL,
            services JSON NOT NULL,
            city_code VARCHAR(64) NOT NULL REFERENCES cities(code),
            address VARCHAR(160) NOT NULL,
            phone VARCHAR(20) NOT NULL,
            lat DOUBLE PRECISION NOT NULL,
            lng DOUBLE PRECISION NOT NULL,
            active BOOLEAN NOT NULL DEFAULT true
        )
    """)
    op.execute("CREATE INDEX IF NOT EXISTS ix_providers_city_code ON providers (city_code)")
    op.execute("""
        CREATE TABLE IF NOT EXISTS assistance_requests (
            request_id VARCHAR(36) PRIMARY KEY,
            user_id VARCHAR(36) NOT NULL REFERENCES insured_accounts(user_id) ON DELETE CASCADE,
            policy_number VARCHAR(16) NOT NULL REFERENCES policies(policy_number),
            service VARCHAR(20) NOT NULL,
            provider_id VARCHAR(16) NOT NULL REFERENCES providers(provider_id),
            city_code VARCHAR(64),
            description VARCHAR(300) NOT NULL,
            distance_km DOUBLE PRECISION,
            eta_minutes INTEGER NOT NULL,
            status VARCHAR(12) NOT NULL,
            created_at TIMESTAMPTZ NOT NULL
        )
    """)
    op.execute("CREATE INDEX IF NOT EXISTS ix_assistance_requests_user_id ON assistance_requests (user_id)")


def downgrade() -> None:
    op.execute("DROP TABLE IF EXISTS assistance_requests")
    op.execute("DROP TABLE IF EXISTS providers")
