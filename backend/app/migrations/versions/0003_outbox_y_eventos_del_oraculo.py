"""Estado de publicación del outbox (RC-14) y eventos del oráculo ya procesados (RC-26), para HU-MOV-10.

Revision ID: 0003
Revises: 0002
Create Date: 2026-11-14
"""
from alembic import op

revision = "0003"
down_revision = "0002"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.execute("ALTER TABLE outbox ADD COLUMN IF NOT EXISTS attempts INTEGER NOT NULL DEFAULT 0")
    op.execute("ALTER TABLE outbox ADD COLUMN IF NOT EXISTS published_at TIMESTAMPTZ")
    op.execute("CREATE INDEX IF NOT EXISTS ix_outbox_status_created ON outbox (status, created_at)")
    op.execute("""
        CREATE TABLE IF NOT EXISTS oracle_events (
            event_id VARCHAR(64) PRIMARY KEY,
            event_type VARCHAR(32) NOT NULL,
            payload JSON NOT NULL,
            claim_numbers JSON NOT NULL,
            received_at TIMESTAMPTZ NOT NULL
        )
    """)


def downgrade() -> None:
    op.execute("DROP TABLE IF EXISTS oracle_events")
    op.execute("DROP INDEX IF EXISTS ix_outbox_status_created")
    op.execute("ALTER TABLE outbox DROP COLUMN IF EXISTS published_at")
    op.execute("ALTER TABLE outbox DROP COLUMN IF EXISTS attempts")
