"""Entorno de Alembic: usa la conexión del arranque o, desde la línea de comandos, la DATABASE_URL de la configuración."""
import asyncio

from alembic import context
from sqlalchemy.ext.asyncio import create_async_engine

import app.main  # noqa: F401 - registra todas las tablas en Base.metadata
from app.core.database import Base

config = context.config
target_metadata = Base.metadata


def run(connection) -> None:
    context.configure(connection=connection, target_metadata=target_metadata, transaction_per_migration=True)
    with context.begin_transaction():
        context.run_migrations()


async def run_from_url() -> None:
    engine = create_async_engine(config.get_main_option("sqlalchemy.url"))
    async with engine.begin() as conn:
        await conn.run_sync(run)
    await engine.dispose()


connection = config.attributes.get("connection")
if connection is not None:
    run(connection)
else:
    asyncio.run(run_from_url())
