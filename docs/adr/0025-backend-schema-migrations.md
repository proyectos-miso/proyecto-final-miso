# ADR 0025 · Backend: schema changes only through versioned migrations

| Field | Value |
|---|---|
| Status | Proposed |
| Date | 2026-11-14 |
| Story | HU-MOV-10 (RC-13) |
| Deciders | The team (to be confirmed) |

## Context

- Until HU-MOV-10 the schema was created with `Base.metadata.create_all` at startup, and every column
  added after the first deploy was appended as an `ALTER TABLE ... ADD COLUMN IF NOT EXISTS` statement in
  `app/main.py`. That block grew to more than forty statements, ran on every start, and had no record of
  which change belonged to which story or whether it had been applied.
- DEV and the local databases already hold data, so the first versioned migration must adopt an existing
  database without touching rows.
- The backend runs as an ECS task built from `backend/Dockerfile`; whatever applies the migrations must be
  inside the image.

## Options considered

| Option | Pros | Cons |
|---|---|---|
| A. Alembic migrations inside the package (`app/migrations/`), applied by the app at startup | One mechanism, versioned and reviewable; the image carries them; no extra deploy step | A failing migration blocks the start; needs a baseline that adopts existing databases |
| B. Alembic as a separate deploy step (`alembic upgrade head` before the service starts) | The app starts only on a migrated schema | One more piece in `deploy.yml` and in local setups that can be forgotten |
| C. Keep `create_all` plus ad hoc `ALTER` statements | No change | No history, no rollback, drift between environments |

## Decision

Option A.

1. Migrations live in `app/migrations/versions/` and are numbered `0001`, `0002`, ...; `alembic.ini` only
   serves the command line (`poetry run alembic revision -m "..."`).
2. `app/core/migrations.upgrade_database()` runs `alembic upgrade head` inside the startup lifespan with
   the app's async engine, before seeds and background tasks.
3. `0001_esquema_inicial` is the baseline: it runs `create_all` and the former `ALTER ... IF NOT EXISTS`
   statements, so it is idempotent and adopts a database created before Alembic without changing data.
   Later migrations use `IF NOT EXISTS` only when they must also run against databases that already got
   the change by hand.
4. `app/main.py` no longer executes DDL. New columns, tables, indexes, sequences and triggers go in a new
   migration file named after the story.
5. Tests in `tests/integration/test_migrations.py` migrate an empty database and a database created the
   old way with data, and run the upgrade twice.

## Consequences

- Each schema change is a reviewable file tied to a story; `alembic_version` says what a database has.
- A migration that fails stops the deploy, which is the intended behaviour: the circuit breaker in ECS
  keeps the previous task running.
- Downgrades exist from `0002` on; the baseline does not downgrade.

## Security and privacy impact

- No data is moved or copied by migrations; personal data stays in the same tables.
- The migration runner uses the task's database credentials; no new secret.
