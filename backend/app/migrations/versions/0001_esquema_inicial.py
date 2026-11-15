"""Esquema inicial: adopta la base creada antes de Alembic sin tocar sus datos (RC-13).

Es idempotente: en una base vacía crea todo; en una existente solo agrega lo que le falte, como hacía el arranque.

Revision ID: 0001
Revises:
Create Date: 2026-11-14
"""
from alembic import op
from sqlalchemy import text

import app.main  # noqa: F401 - registra todas las tablas en Base.metadata
from app.core.database import Base

revision = "0001"
down_revision = None
branch_labels = None
depends_on = None


def upgrade() -> None:
    Base.metadata.create_all(op.get_bind())
    op.execute(text("ALTER TABLE quotes ADD COLUMN IF NOT EXISTS partner_id VARCHAR(64)"))
    op.execute(text("CREATE INDEX IF NOT EXISTS ix_quotes_partner_id ON quotes (partner_id)"))
    # HU-WEB-08: socio de la póliza (faltaba desde HU-WEB-14), usuarios ligados a un socio y titular
    # de la póliza en bases ya creadas.
    op.execute(text(
        "ALTER TABLE policies ADD COLUMN IF NOT EXISTS partner_id VARCHAR(64) REFERENCES partners(partner_id)"
    ))
    op.execute(text("CREATE INDEX IF NOT EXISTS ix_policies_partner_id ON policies (partner_id)"))
    op.execute(text(
        "ALTER TABLE users ADD COLUMN IF NOT EXISTS partner_id VARCHAR(64) REFERENCES partners(partner_id)"
    ))
    op.execute(text("ALTER TABLE users ADD COLUMN IF NOT EXISTS last_totp_step INTEGER"))
    op.execute(text(
        "ALTER TABLE policies ADD COLUMN IF NOT EXISTS holder_name VARCHAR(150), "
        "ADD COLUMN IF NOT EXISTS holder_document_type VARCHAR(8), "
        "ADD COLUMN IF NOT EXISTS holder_email VARCHAR(254), "
        "ADD COLUMN IF NOT EXISTS holder_phone VARCHAR(20)"
    ))
    # HU-WEB-16: clave de la carátula en S3, vacía hasta el primer pedido.
    op.execute(text("ALTER TABLE policies ADD COLUMN IF NOT EXISTS pdf_s3_key VARCHAR(64)"))
    op.execute(text("CREATE INDEX IF NOT EXISTS ix_policies_insured_document ON policies (insured_document)"))
    # HU-WEB-12: cuota por socio con ventana temporal compartida entre réplicas
    op.execute(text("ALTER TABLE partners ADD COLUMN IF NOT EXISTS quota_window_seconds INTEGER NOT NULL DEFAULT 3600"))
    op.execute(text("ALTER TABLE partners ADD COLUMN IF NOT EXISTS quota_reset_at TIMESTAMPTZ"))
    op.execute(text("ALTER TABLE partners ADD COLUMN IF NOT EXISTS environment VARCHAR(16) NOT NULL DEFAULT 'production'"))
    op.execute(text("ALTER TABLE partners ALTER COLUMN environment SET DEFAULT 'production'"))
    op.execute(text("UPDATE partners SET environment = lower(environment) WHERE environment <> lower(environment)"))
    op.execute(text("ALTER TABLE partners ADD COLUMN IF NOT EXISTS contact_email VARCHAR(254)"))
    # HU-WEB-12 Brechas: campos de consentimiento y crédito en cotizaciones (RC-06, RC-08)
    op.execute(text("ALTER TABLE quotes ADD COLUMN IF NOT EXISTS consent_id VARCHAR(36)"))
    op.execute(text("ALTER TABLE quotes ADD COLUMN IF NOT EXISTS financial_entity VARCHAR(120)"))
    op.execute(text("ALTER TABLE quotes ADD COLUMN IF NOT EXISTS term_months INTEGER"))
    op.execute(text("ALTER TABLE quotes ADD COLUMN IF NOT EXISTS birth_date TIMESTAMPTZ"))
    # HU-WEB-12 Brechas: tabla inmutable policy_signatures (RC-02, CTL-07)
    op.execute(text("""
        CREATE TABLE IF NOT EXISTS policy_signatures (
            signature_id VARCHAR(36) PRIMARY KEY,
            policy_id VARCHAR(16) NOT NULL REFERENCES policies(policy_number),
            policy_number VARCHAR(16) NOT NULL,
            document_type VARCHAR(8) NOT NULL,
            document_number VARCHAR(32) NOT NULL,
            signed_at TIMESTAMPTZ NOT NULL,
            otp_code_hash VARCHAR(64) NOT NULL,
            pdf_hash_sha256 VARCHAR(64) NOT NULL,
            consent_id VARCHAR(64)
        )
    """))
    op.execute(text("CREATE INDEX IF NOT EXISTS ix_policy_signatures_policy_id ON policy_signatures(policy_id)"))
    op.execute(text("""
        CREATE OR REPLACE FUNCTION policy_signatures_immutable() RETURNS trigger AS $$
        BEGIN
            RAISE EXCEPTION 'Las firmas de póliza no se pueden modificar ni borrar';
        END;
        $$ LANGUAGE plpgsql
    """))
    op.execute(text("""
        DO $$
        BEGIN
            IF NOT EXISTS (
                SELECT 1 FROM pg_trigger WHERE tgname = 'policy_signatures_no_change'
            ) THEN
                CREATE TRIGGER policy_signatures_no_change
                    BEFORE UPDATE OR DELETE ON policy_signatures
                    FOR EACH ROW EXECUTE FUNCTION policy_signatures_immutable();
            END IF;
        END
        $$;
    """))
    # HU-WEB-15: versión de la póliza por endosos y datos del siniestro para el expediente y el dictamen.
    op.execute(text("ALTER TABLE policies ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 1"))
    op.execute(text(
        "ALTER TABLE claims ADD COLUMN IF NOT EXISTS origin VARCHAR(16) NOT NULL DEFAULT 'APP', "
        "ADD COLUMN IF NOT EXISTS coverage_id VARCHAR(32), "
        "ADD COLUMN IF NOT EXISTS description TEXT, "
        "ADD COLUMN IF NOT EXISTS parametric_event JSON, "
        "ADD COLUMN IF NOT EXISTS assigned_to VARCHAR(120), "
        "ADD COLUMN IF NOT EXISTS expert_id VARCHAR(32) REFERENCES experts(expert_id), "
        "ADD COLUMN IF NOT EXISTS settled_amount DOUBLE PRECISION"
    ))
    # HU-MOV-09: el mismo reporte del teléfono es el mismo siniestro.
    op.execute(text("ALTER TABLE claims ADD COLUMN IF NOT EXISTS client_report_id VARCHAR(36)"))
    op.execute(text(
        "CREATE UNIQUE INDEX IF NOT EXISTS ix_claims_client_report_id ON claims (client_report_id)"
    ))
    op.execute(text("CREATE SEQUENCE IF NOT EXISTS claim_number_seq"))


def downgrade() -> None:
    raise NotImplementedError("El esquema inicial no se revierte.")
