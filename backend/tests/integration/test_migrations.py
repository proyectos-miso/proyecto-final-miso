"""Migraciones versionadas con Alembic contra PostgreSQL (HU-MOV-10, RC-13)."""
import asyncio
import uuid

import pytest
from alembic import command
from sqlalchemy import text
from sqlalchemy.engine import make_url
from sqlalchemy.ext.asyncio import create_async_engine

from app.core.config import settings
from app.core.database import Base
from app.core.migrations import alembic_config

pytestmark = pytest.mark.integration


@pytest.fixture
async def empty_database():
    name = f"solventa_mig_{uuid.uuid4().hex[:8]}"
    url = make_url(settings.DATABASE_URL).set(database=name)
    admin = create_async_engine(url.set(database="postgres"), isolation_level="AUTOCOMMIT")
    async with admin.connect() as conn:
        await conn.execute(text(f'CREATE DATABASE "{name}"'))
    yield url
    async with admin.connect() as conn:
        await conn.execute(text(f'DROP DATABASE IF EXISTS "{name}" WITH (FORCE)'))
    await admin.dispose()


def config_for(url):
    config = alembic_config()
    config.set_main_option("sqlalchemy.url", url.render_as_string(hide_password=False))
    return config


async def upgrade(url, revision: str = "head") -> None:
    await asyncio.to_thread(command.upgrade, config_for(url), revision)


async def scalar(url, sql: str):
    engine = create_async_engine(url)
    async with engine.connect() as conn:
        value = (await conn.execute(text(sql))).scalar()
    await engine.dispose()
    return value


async def test_una_base_vacia_queda_en_la_ultima_migracion_con_todo_el_esquema(empty_database):
    await upgrade(empty_database)

    assert await scalar(empty_database, "SELECT version_num FROM alembic_version") == "0007"
    assert await scalar(empty_database, "SELECT count(*) FROM information_schema.columns "
                                        "WHERE table_name = 'quotes' AND column_name = 'city'") == 1
    assert await scalar(empty_database, "SELECT count(*) FROM pg_class WHERE relname = 'claim_number_seq'") == 1
    assert await scalar(empty_database, "SELECT count(*) FROM pg_trigger WHERE tgname = 'policy_signatures_no_change'") == 1
    assert await scalar(empty_database, "SELECT count(*) FROM pg_trigger WHERE tgname = 'quotes_no_change'") == 1


async def test_una_base_creada_antes_de_alembic_se_adopta_sin_perder_datos(empty_database):
    engine = create_async_engine(empty_database)
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
        await conn.execute(text("ALTER TABLE quotes DROP COLUMN city"))
        await conn.execute(text(
            "INSERT INTO partners (partner_id, name, client_id, client_secret_hash, status, quota_limit, quota_used, "
            "quota_window_seconds, environment) "
            "VALUES ('socio-prueba', 'Socio de prueba', 'cliente-prueba', 'hash', 'ACTIVE', 10, 0, 3600, 'sandbox')"
        ))
    await engine.dispose()

    await upgrade(empty_database)
    await upgrade(empty_database)

    assert await scalar(empty_database, "SELECT name FROM partners WHERE partner_id = 'socio-prueba'") == "Socio de prueba"
    assert await scalar(empty_database, "SELECT version_num FROM alembic_version") == "0007"
    assert await scalar(empty_database, "SELECT count(*) FROM information_schema.columns "
                                        "WHERE table_name = 'quotes' AND column_name = 'city'") == 1


async def test_la_migracion_de_preferencias_crea_su_tabla_desde_0003_y_se_revierte(empty_database):
    await upgrade(empty_database, "0003")
    engine = create_async_engine(empty_database)
    async with engine.begin() as conn:
        await conn.execute(text("DROP TABLE push_preferences"))
    await engine.dispose()

    await upgrade(empty_database)
    created = await scalar(empty_database, "SELECT column_default FROM information_schema.columns "
                                           "WHERE table_name = 'push_preferences' AND column_name = 'claims'")
    await asyncio.to_thread(command.downgrade, config_for(empty_database), "0003")

    assert created == "true"
    assert await scalar(empty_database, "SELECT to_regclass('push_preferences')") is None
    assert await scalar(empty_database, "SELECT version_num FROM alembic_version") == "0003"


async def test_la_migracion_de_auditoria_agrega_las_entradas_y_hace_inmutables_las_cotizaciones_desde_0004(empty_database):
    await upgrade(empty_database, "0004")
    engine = create_async_engine(empty_database)
    async with engine.begin() as conn:
        await conn.execute(text("DROP TRIGGER quotes_no_change ON quotes"))
        await conn.execute(text("DROP FUNCTION quotes_immutable()"))
        await conn.execute(text("ALTER TABLE quotes DROP COLUMN rules_version, DROP COLUMN insured_amount, "
                                "DROP COLUMN occupation_risk"))
        await conn.execute(text("ALTER TABLE underwriting_rejections DROP COLUMN rules_version"))
    await engine.dispose()
    columns = ("SELECT count(*) FROM information_schema.columns WHERE (table_name = 'quotes' AND column_name IN "
               "('rules_version', 'insured_amount', 'occupation_risk')) OR (table_name = 'underwriting_rejections' "
               "AND column_name = 'rules_version')")
    trigger = "SELECT count(*) FROM pg_trigger WHERE tgname = 'quotes_no_change'"

    await upgrade(empty_database)
    await upgrade(empty_database)
    added, triggers = await scalar(empty_database, columns), await scalar(empty_database, trigger)
    personal_columns = await scalar(empty_database, "SELECT count(*) FROM information_schema.columns WHERE table_name = "
                                                 "'underwriting_rejections' AND column_name IN ('score', 'birth_date')")
    await asyncio.to_thread(command.downgrade, config_for(empty_database), "0004")

    assert (added, triggers, personal_columns) == (4, 1, 0)
    assert await scalar(empty_database, columns) == 0
    assert await scalar(empty_database, trigger) == 0
    assert await scalar(empty_database, "SELECT version_num FROM alembic_version") == "0004"


async def test_la_migracion_de_mensajes_procesados_crea_su_tabla_desde_0005_y_se_revierte(empty_database):
    await upgrade(empty_database, "0005")
    engine = create_async_engine(empty_database)
    async with engine.begin() as conn:
        await conn.execute(text("DROP TABLE processed_messages"))
    await engine.dispose()
    table = "SELECT to_regclass('processed_messages')::text"

    await upgrade(empty_database)
    await upgrade(empty_database)
    created = await scalar(empty_database, table)
    key = await scalar(empty_database, "SELECT count(*) FROM information_schema.table_constraints WHERE "
                                       "table_name = 'processed_messages' AND constraint_type = 'PRIMARY KEY'")
    await asyncio.to_thread(command.downgrade, config_for(empty_database), "0005")

    assert (created, key) == ("processed_messages", 1)
    assert await scalar(empty_database, table) is None
    assert await scalar(empty_database, "SELECT version_num FROM alembic_version") == "0005"


async def test_la_migracion_de_verificacion_de_identidad_aprueba_las_cuentas_existentes_desde_0006(empty_database):
    await upgrade(empty_database, "0006")
    engine = create_async_engine(empty_database)
    async with engine.begin() as conn:
        await conn.execute(text("DROP TABLE identity_verifications"))
        await conn.execute(text("ALTER TABLE insured_accounts DROP COLUMN document_type, "
                                "DROP COLUMN verification_status, DROP COLUMN verified_at"))
        await conn.execute(text("ALTER TABLE document_otps DROP COLUMN binding_hash"))
        await conn.execute(text(
            "INSERT INTO insured_accounts (user_id, email, full_name, document_number, phone, password_hash, "
            "token_version) VALUES ('ins-prueba', 'previa@example.com', 'Cuenta Previa', '52000999', "
            "'+573000000000', 'hash', 0)"
        ))
    await engine.dispose()
    columns = ("SELECT count(*) FROM information_schema.columns WHERE (table_name = 'insured_accounts' "
               "AND column_name IN ('document_type', 'verification_status', 'verified_at')) OR "
               "(table_name = 'document_otps' AND column_name = 'binding_hash')")
    table = "SELECT to_regclass('identity_verifications')::text"

    await upgrade(empty_database)
    await upgrade(empty_database)
    added, created = await scalar(empty_database, columns), await scalar(empty_database, table)
    status = await scalar(empty_database, "SELECT verification_status || '/' || document_type FROM insured_accounts")
    await asyncio.to_thread(command.downgrade, config_for(empty_database), "0006")

    assert (added, created, status) == (4, "identity_verifications", "APPROVED/CC")
    assert await scalar(empty_database, columns) == 0
    assert await scalar(empty_database, table) is None
    assert await scalar(empty_database, "SELECT version_num FROM alembic_version") == "0006"


CONSENT = (
    "INSERT INTO consents (id, document_type, document_number, channel, purpose, scope, validity_days, status, "
    "consent_hash, granted_at, expires_at) VALUES ('{id}', 'CC', '{document}', '{channel}', '{purpose}', 'Alcance', "
    "30, 'GRANTED', repeat('a', 64), now(), now() + interval '30 days')"
)


async def test_revertir_la_verificacion_de_identidad_borra_las_cuentas_no_aprobadas_y_las_autorizaciones_biometricas(
    empty_database,
):
    await upgrade(empty_database)
    engine = create_async_engine(empty_database)
    async with engine.begin() as conn:
        for user_id, status in (("ins-aprobada", "APPROVED"), ("ins-pendiente", "PENDING"), ("ins-rechazada", "REJECTED")):
            await conn.execute(text(
                "INSERT INTO insured_accounts (user_id, email, full_name, document_type, document_number, phone, "
                f"password_hash, token_version, verification_status) VALUES ('{user_id}', '{user_id}@example.com', "
                f"'Cuenta Prueba', 'CC', '5200{len(user_id)}', '+573000000000', 'hash', 0, '{status}')"
            ))
        await conn.execute(text(
            "INSERT INTO identity_verifications (verification_id, user_id, status, created_at) "
            "VALUES ('ver-pendiente', 'ins-pendiente', 'PENDING', now())"
        ))
        await conn.execute(text(
            "INSERT INTO insured_devices (device_id, user_id, secret_hash, created_at) "
            "VALUES ('dev-pendiente', 'ins-pendiente', repeat('b', 64), now())"
        ))
        await conn.execute(text(CONSENT.format(id="CNS-BIO", document="52000099", channel="app",
                                               purpose="biometric_verification")))
        await conn.execute(text(CONSENT.format(id="CNS-OF", document="52000099", channel="web",
                                               purpose="Perfilamiento")))
    await engine.dispose()

    await asyncio.to_thread(command.downgrade, config_for(empty_database), "0006")

    assert await scalar(empty_database, "SELECT string_agg(user_id, ',') FROM insured_accounts") == "ins-aprobada"
    assert await scalar(empty_database, "SELECT count(*) FROM insured_devices") == 0
    assert await scalar(empty_database, "SELECT string_agg(id, ',') FROM consents") == "CNS-OF"
    assert await scalar(empty_database, "SELECT version_num FROM alembic_version") == "0006"
