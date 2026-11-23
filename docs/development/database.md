# Database

Flyway owns the schema. Hibernate validates it and never emits DDL: `ddl-auto` is
`validate` in `application.yaml` and `none` in `application-test.yaml`. Every rule below is
enforced by a test or by the database itself.

## Contents

1. [Migration naming](#migration-naming)
2. [Enumerated columns carry a CHECK](#enumerated-columns-carry-a-check)
3. [Never modify an applied migration](#never-modify-an-applied-migration)
4. [Ordering across branches](#ordering-across-branches)
5. [Realigning an existing database](#realigning-an-existing-database)
6. [Database roles](#database-roles)

---

## Migration naming

Migrations are versioned by **timestamp**, not by sequence:

```
V<yyyyMMddHHmm>__snake_case_name.sql
```

- Valid — `V202605270702__initial_schema.sql`
- Invalid — `V2__add_idempotency_keys.sql` (sequential version)
- Invalid — `V202605270702__addIdempotencyKeys.sql` (not `snake_case`)

A sequential version collides as soon as two branches add a migration in parallel: both
claim the next integer, and the clash only surfaces at merge. Generate the timestamp with:

```bash
date -u +%Y%m%d%H%M
```

`MigrationNamingConventionTest` scans `src/main/resources/db/migration/` on every
`./gradlew test` and fails on any file that breaks the pattern.

## Enumerated columns carry a CHECK

A column that stores an enum name is a closed set, and the schema has to say so. Every one
of them carries a constraint named `ck_<table>_<column>` listing the values verbatim:

```sql
ALTER TABLE transactions
    ADD CONSTRAINT ck_transactions_type
        CHECK (type IN ('CASH_IN', 'CASH_OUT', 'P2P_TRANSFER'));
```

Nullable columns need no `IS NULL` branch: a `CHECK` fails only on FALSE, and
`NULL IN (...)` is UNKNOWN.

**Adding or removing a constant is a migration**, dropping and re-adding the constraint.
Forgetting it does not slip through — `EnumColumnConstraintsDbTest` in `auth` and in
`payment` reads the definition back out of `pg_get_constraintdef` and asserts the literals
**equal** `Enum.values()`. The equality cuts both ways: a constant added only in Java fails,
and a constraint still listing a constant the domain dropped fails too.

Native `CREATE TYPE ... AS ENUM` was considered and rejected; the reasoning is in the header
of `V202609071725__constrain_enum_columns.sql`.

## Never modify an applied migration

Add a new one instead. Flyway stores a checksum of each migration's content in
`flyway_schema_history`; editing a file already applied anywhere invalidates that checksum
and blocks startup on every database that holds it.

### The everyday case: your own migration, still on your branch

The rule is about migrations that have been **shared**. A migration you are still writing
hits the same wall for a much smaller reason: you ran the application once, the dev database
applied the file, and you then edited it — even only a comment. Startup refuses:

```
Migration checksum mismatch for migration version 202609071725
-> Applied to database : -1060536491
-> Resolved locally    : -1364846286
```

Flyway compares the **whole file**, comments included, so a reworded header breaks the
checksum exactly as a rewritten `ALTER TABLE` would.

**First, find out which of the two it was.** Read the schema back and compare it with the
file — never assume the edit was cosmetic:

```bash
docker compose exec postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c \
  "SELECT conname, pg_get_constraintdef(oid) FROM pg_constraint
    WHERE conname LIKE 'ck_%' ORDER BY conname;"
```

Then pick:

- **The database is disposable** — the usual answer on a feature branch. Recreate it and let
  Flyway replay the whole chain from nothing:

  ```bash
  docker compose down -v && docker compose up -d
  ```

  This proves the migration applies, rather than declaring that it did, and it exercises the
  chain on the dev engine — the Testcontainers suite runs `postgres:16-alpine`, the dev
  container is on 17.x.

- **The database holds something you need** — realign the checksum by hand, but only after
  the comparison above showed the applied DDL matches the file:

  ```bash
  docker compose exec postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c \
    "UPDATE flyway_schema_history SET checksum = <resolved locally>
      WHERE version = '<version>';"
  ```

  Take `<resolved locally>` from the error message. Unlike the rename case below, this works:
  the version still exists on disk, so it is the checksum and not the identity that has to
  move.

**Once the migration is pushed, neither applies.** Someone else's database holds it, and the
only correct answer is a new migration.

## Ordering across branches

`spring.flyway.out-of-order=false`, on every profile. A migration whose version is lower than
the highest already applied is never inserted retroactively.

**The cost we accept**: a branch opened Monday and merged Friday can carry a timestamp older
than a migration already applied on `develop`. Startup then fails:

```
Detected resolved migration not applied to database: 202605270702
```

**The fix**: before merging, `git mv` the migration to a fresh timestamp so its version is
the highest. On the feature branch — never after the merge.

`out-of-order=true` was rejected: it makes the applied order a property of each
environment's history rather than of the repository, so two databases that received the same
migrations in a different order can diverge with nothing in the repository to arbitrate.

## Realigning an existing database

`V1__initial_schema.sql` became `V202605270702__initial_schema.sql`. The version changed, so
a database that already applied `V1` hits two problems: an applied migration missing from
disk, and a higher pending version it would replay over tables that already exist.

One statement fixes both. The checksum covers file **content**, which did not change, so it
stays valid:

```bash
docker compose exec postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c \
  "UPDATE flyway_schema_history
      SET version = '202605270702',
          script  = 'V202605270702__initial_schema.sql'
    WHERE version = '1' AND script = 'V1__initial_schema.sql';"
```

`flyway repair` does not help: it matches by version, and version `1` no longer exists on
disk. If the database holds nothing worth keeping, drop it instead:

```bash
docker compose down -v && docker compose up -d postgres
```

Testcontainers-backed tests need neither: each run starts an empty database and replays
every migration.

## Database roles

The application never connects as the superuser. `docker/postgres/initdb/10-kora-roles.sh`
creates two roles from `.env` and makes the first one owner of the schema:

| Role | Used by | Rights |
|---|---|---|
| `POSTGRES_MIGRATION_USER` (`kora_migration`) | Flyway, the perf `/test/reset` | owner: DDL, `TRUNCATE` |
| `POSTGRES_APP_USER` (`kora_app`) | the application | `SELECT` `INSERT` `UPDATE` `DELETE`, no `UPDATE` or `DELETE` on `ledger_entries` and `trx_state_historics` |

The DML rights come from `V202610171921__grant_application_role.sql`, which also covers every
table created afterwards. A new append-only table revokes `UPDATE` and `DELETE` from
`${app_role}` in its own migration. `ApplicationDatabaseRoleTest` asserts the split.

PostgreSQL runs the script on a **fresh volume only**. A volume created before it keeps the
superuser as owner of every table. Recreate it if it holds nothing worth keeping:

```bash
docker compose down -v && docker compose up -d postgres
```

Otherwise run the script by hand. It creates the missing roles and transfers the schema, its
tables and its functions to the migration role; running it twice changes nothing:

```bash
docker compose exec postgres sh /docker-entrypoint-initdb.d/10-kora-roles.sh
```

The next start then applies the grants migration as the new owner.
