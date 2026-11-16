"""Arranque del backend: crea tablas y semillas, y no se cae si la base de datos no responde."""
import pytest
from sqlalchemy import make_url, select
from sqlalchemy.ext.asyncio import async_sessionmaker, create_async_engine

from app import main
from app.core import migrations
from app.core.config import settings
from app.modules.assistance.models import ProviderDB
from app.modules.auth.models import UserDB
from app.modules.identity.db_models import InsuredAccountDB
from tests.db_name import TEST_DATABASE

pytestmark = pytest.mark.integration


async def test_arranque_crea_tablas_y_la_asegurada_de_prueba(postgres_session, monkeypatch):
    engine = create_async_engine(make_url(settings.DATABASE_URL).set(database=TEST_DATABASE))
    monkeypatch.setattr(main, "engine", engine)
    monkeypatch.setattr(migrations, "engine", engine)
    monkeypatch.setattr(main, "AsyncSessionLocal", async_sessionmaker(engine, expire_on_commit=False))

    async with main.lifespan(main.app):
        pass

    accounts = (await postgres_session.execute(select(InsuredAccountDB.email))).scalars().all()
    assert "maria.ruiz@correo.co" in accounts
    providers = (await postgres_session.execute(select(ProviderDB.phone))).scalars().all()
    assert len(providers) == 18 and all(p[6:9] == "555" for p in providers)
    await engine.dispose()


async def test_tc_08_con_environment_production_no_siembra_cuentas(postgres_session, monkeypatch):
    engine = create_async_engine(make_url(settings.DATABASE_URL).set(database=TEST_DATABASE))
    monkeypatch.setattr(main, "engine", engine)
    monkeypatch.setattr(migrations, "engine", engine)
    monkeypatch.setattr(main, "AsyncSessionLocal", async_sessionmaker(engine, expire_on_commit=False))
    monkeypatch.setattr(settings, "ENVIRONMENT", "production")

    async with main.lifespan(main.app):
        pass

    assert (await postgres_session.execute(select(UserDB.username))).scalars().all() == []
    assert (await postgres_session.execute(select(InsuredAccountDB.email))).scalars().all() == []
    # Mejora FE-MOV-07: los prestadores ficticios no llegan a producción.
    assert (await postgres_session.execute(select(ProviderDB.provider_id))).scalars().all() == []
    await engine.dispose()


async def test_arranque_sin_base_de_datos_avisa_y_sigue(monkeypatch, capsys):
    engine = create_async_engine("postgresql+asyncpg://nadie:nada@127.0.0.1:1/ninguna")
    monkeypatch.setattr(main, "engine", engine)
    monkeypatch.setattr(migrations, "engine", engine)

    async with main.lifespan(main.app):
        pass

    assert "Aviso de inicialización" in capsys.readouterr().out
    await engine.dispose()
