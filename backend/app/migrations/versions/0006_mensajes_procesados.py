"""Mensajes de la cola de eventos ya procesados por el consumidor del outbox (ADR 0030).

Revision ID: 0006
Revises: 0005
Create Date: 2026-11-15
"""
from alembic import op

revision = "0006"
down_revision = "0005"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.execute("""
        CREATE TABLE IF NOT EXISTS processed_messages (
            message_id VARCHAR(64) PRIMARY KEY,
            processed_at TIMESTAMPTZ NOT NULL
        )
    """)


def downgrade() -> None:
    op.execute("DROP TABLE IF EXISTS processed_messages")
