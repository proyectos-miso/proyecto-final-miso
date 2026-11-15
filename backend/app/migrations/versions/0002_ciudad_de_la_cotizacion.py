"""Ciudad del asegurado en la cotización, para cruzar los eventos del oráculo con las pólizas (HU-MOV-10, RC-26).

Revision ID: 0002
Revises: 0001
Create Date: 2026-11-14
"""
from alembic import op

revision = "0002"
down_revision = "0001"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.execute("ALTER TABLE quotes ADD COLUMN IF NOT EXISTS city VARCHAR(80)")
    op.execute("CREATE INDEX IF NOT EXISTS ix_quotes_city ON quotes (city)")


def downgrade() -> None:
    op.execute("DROP INDEX IF EXISTS ix_quotes_city")
    op.execute("ALTER TABLE quotes DROP COLUMN IF EXISTS city")
