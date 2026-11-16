"""Registro desde la app con documento y selfie: estado de verificación de la cuenta (mejora FE-MOV-01).

El downgrade no puede dejar cuentas sin identidad aprobada: el código anterior no conoce el estado y les mostraría
las pólizas del documento. Por eso primero borra las cuentas que no estén APPROVED (con sus verificaciones, huellas y
códigos, por la llave foránea), todas las autorizaciones biométricas, que el código anterior tomaría como
consentimientos Open Finance, y los códigos de registro; después quita las columnas. Las imágenes que queden en S3
bajo identidad/ las vence la regla de ciclo de vida del bucket.

Revision ID: 0007
Revises: 0006
Create Date: 2026-11-15
"""
from alembic import op

revision = "0007"
down_revision = "0006"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.execute("""
        ALTER TABLE insured_accounts
            ADD COLUMN IF NOT EXISTS document_type VARCHAR(4) NOT NULL DEFAULT 'CC',
            ADD COLUMN IF NOT EXISTS verification_status VARCHAR(10) NOT NULL DEFAULT 'APPROVED',
            ADD COLUMN IF NOT EXISTS verified_at TIMESTAMPTZ
    """)
    op.execute("ALTER TABLE document_otps ADD COLUMN IF NOT EXISTS binding_hash VARCHAR(64)")
    op.execute("""
        CREATE TABLE IF NOT EXISTS identity_verifications (
            verification_id VARCHAR(36) PRIMARY KEY,
            user_id VARCHAR(36) NOT NULL REFERENCES insured_accounts(user_id) ON DELETE CASCADE,
            status VARCHAR(10) NOT NULL,
            provider_ref VARCHAR(64),
            s3_keys JSON,
            created_at TIMESTAMPTZ NOT NULL,
            decided_at TIMESTAMPTZ
        )
    """)
    op.execute("CREATE INDEX IF NOT EXISTS ix_identity_verifications_user_id ON identity_verifications (user_id)")
    # La autorización biométrica se revoca por cuenta: dos cuentas pueden haber usado el mismo documento.
    op.execute("ALTER TABLE consents ADD COLUMN IF NOT EXISTS user_id VARCHAR(36)")
    op.execute("CREATE INDEX IF NOT EXISTS ix_consents_user_id ON consents (user_id)")


def downgrade() -> None:
    op.execute("DELETE FROM insured_accounts WHERE verification_status <> 'APPROVED'")
    op.execute("DELETE FROM consents WHERE purpose = 'biometric_verification'")
    op.execute("DELETE FROM document_otps WHERE purpose = 'registration'")
    op.execute("DROP INDEX IF EXISTS ix_consents_user_id")
    op.execute("ALTER TABLE consents DROP COLUMN IF EXISTS user_id")
    op.execute("ALTER TABLE document_otps DROP COLUMN IF EXISTS binding_hash")
    op.execute("DROP TABLE IF EXISTS identity_verifications")
    op.execute("""
        ALTER TABLE insured_accounts
            DROP COLUMN IF EXISTS verified_at,
            DROP COLUMN IF EXISTS verification_status,
            DROP COLUMN IF EXISTS document_type
    """)
