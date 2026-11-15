"""Preferencias de avisos del asegurado por categoría (mejora FE-MOV-04).

Revision ID: 0004
Revises: 0003
Create Date: 2026-11-14
"""
from alembic import op

revision = "0004"
down_revision = "0003"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.execute("""
        CREATE TABLE IF NOT EXISTS push_preferences (
            document_number VARCHAR(20) PRIMARY KEY,
            claims BOOLEAN NOT NULL DEFAULT TRUE,
            renewals BOOLEAN NOT NULL DEFAULT TRUE,
            payments BOOLEAN NOT NULL DEFAULT TRUE,
            updated_at TIMESTAMPTZ NOT NULL
        )
    """)


def downgrade() -> None:
    op.execute("DROP TABLE IF EXISTS push_preferences")
