"""Documentos escaneados que el asegurado agrega a un siniestro abierto (mejora FE-MOV-05).

Revision ID: 0008
Revises: 0007
Create Date: 2026-11-15
"""
from alembic import op

revision = "0008"
down_revision = "0007"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.execute("""
        CREATE TABLE IF NOT EXISTS claim_documents (
            document_id VARCHAR(36) PRIMARY KEY,
            claim_number VARCHAR(20) NOT NULL REFERENCES claims(claim_number),
            upload_id VARCHAR(36) NOT NULL,
            position INTEGER NOT NULL,
            s3_key VARCHAR(160) NOT NULL,
            sha256 VARCHAR(64) NOT NULL,
            content_type VARCHAR(32) NOT NULL,
            size_bytes INTEGER NOT NULL,
            uploaded_at TIMESTAMPTZ NOT NULL,
            CONSTRAINT uq_claim_documents_upload_position UNIQUE (upload_id, position)
        )
    """)
    op.execute("CREATE INDEX IF NOT EXISTS ix_claim_documents_claim_number ON claim_documents (claim_number)")


def downgrade() -> None:
    op.execute("DROP TABLE IF EXISTS claim_documents")
