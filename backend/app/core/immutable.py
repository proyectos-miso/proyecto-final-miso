"""Tablas de solo inserción: PostgreSQL rechaza UPDATE y DELETE sobre ellas (HA-17)."""
from sqlalchemy import DDL, Table, event


def make_immutable(table: Table, message: str) -> None:
    """Crea con la tabla una función y un disparador que impiden cambiar o borrar sus filas.
    asyncpg ejecuta una sentencia por llamada: la función y el disparador van por separado."""
    name = table.name
    statements = (
        f"""
        CREATE OR REPLACE FUNCTION {name}_immutable() RETURNS trigger AS $$
        BEGIN
            RAISE EXCEPTION '{message}';
        END;
        $$ LANGUAGE plpgsql
        """,
        f"""
        CREATE TRIGGER {name}_no_change
            BEFORE UPDATE OR DELETE ON {name}
            FOR EACH ROW EXECUTE FUNCTION {name}_immutable()
        """,
    )
    for statement in statements:
        event.listen(table, "after_create", DDL(statement).execute_if(dialect="postgresql"))
