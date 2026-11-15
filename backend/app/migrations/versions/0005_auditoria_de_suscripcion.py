"""Entradas de la auditoría de suscripción y cotizaciones inmutables (mejora FE-WEB-08).

Revision ID: 0005
Revises: 0004
Create Date: 2026-11-15
"""
from alembic import op

revision = "0005"
down_revision = "0004"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.execute(
        "ALTER TABLE quotes ADD COLUMN IF NOT EXISTS rules_version VARCHAR(16), "
        "ADD COLUMN IF NOT EXISTS insured_amount DOUBLE PRECISION, "
        "ADD COLUMN IF NOT EXISTS occupation_risk INTEGER"
    )
    op.execute("ALTER TABLE underwriting_rejections ADD COLUMN IF NOT EXISTS rules_version VARCHAR(16)")
    op.execute("""
        CREATE OR REPLACE FUNCTION quotes_immutable() RETURNS trigger AS $$
        BEGIN
            RAISE EXCEPTION 'Las cotizaciones no se pueden modificar ni borrar';
        END;
        $$ LANGUAGE plpgsql
    """)
    op.execute("""
        DO $$
        BEGIN
            IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'quotes_no_change') THEN
                CREATE TRIGGER quotes_no_change
                    BEFORE UPDATE OR DELETE ON quotes
                    FOR EACH ROW EXECUTE FUNCTION quotes_immutable();
            END IF;
        END
        $$;
    """)


def downgrade() -> None:
    op.execute("DROP TRIGGER IF EXISTS quotes_no_change ON quotes")
    op.execute("DROP FUNCTION IF EXISTS quotes_immutable()")
    op.execute("ALTER TABLE underwriting_rejections DROP COLUMN IF EXISTS rules_version")
    op.execute(
        "ALTER TABLE quotes DROP COLUMN IF EXISTS occupation_risk, "
        "DROP COLUMN IF EXISTS insured_amount, "
        "DROP COLUMN IF EXISTS rules_version"
    )
