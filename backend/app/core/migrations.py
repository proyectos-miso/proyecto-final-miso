"""El esquema de la base cambia solo con migraciones versionadas de Alembic, que el arranque aplica (RC-13)."""
from pathlib import Path

from alembic import command
from alembic.config import Config

from app.core.config import settings
from app.core.database import engine

MIGRATIONS = Path(__file__).resolve().parents[1] / "migrations"


def alembic_config() -> Config:
    config = Config()
    config.set_main_option("script_location", str(MIGRATIONS))
    config.set_main_option("sqlalchemy.url", settings.DATABASE_URL)
    return config


def _upgrade(connection) -> None:
    config = alembic_config()
    config.attributes["connection"] = connection
    command.upgrade(config, "head")


async def upgrade_database() -> None:
    """Lleva la base a la última migración; una base creada antes de Alembic la adopta la primera, que es idempotente."""
    async with engine.begin() as conn:
        await conn.run_sync(_upgrade)
