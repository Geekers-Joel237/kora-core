# ADR-007 — Closed Enumeration Enforcement at the Database Layer

**Date**: 2026-08-30
**Status**: Accepted
**Authors**: Kora Core Engineering
**Supersedes**: None
**Related**: ADR-001 (immutable ledger), ADR-002 (payment lifecycle)

---

## Context

Every "enum-shaped" column in the schema — `users.role`, `users.status`,
`accounts.resource_type`, `transactions.state`, `transactions.type`,
`transactions.payment_method`, `operations.type`,
`trx_state_historics.old_state`, `.new_state`, `.triggered_by`,
`authorization_records.status` — was declared as a free `VARCHAR(255)`.

Java enforces a closed set on the way *in*, through the domain enums and
`TransactionState.fromValue()`. Nothing enforced it in the database itself.
A migration, a hotfix script, or a data import could write a value the
domain doesn't recognize. That row reads back fine as raw SQL, but the
moment the application maps it back to a domain object —
`TransactionState.fromValue()`, `TriggerSource.valueOf()`,
`AuthorizationRecord.AuthorizationStatus.valueOf()` — it throws
`IllegalArgumentException`, corrupting that read (or that aggregate) at an
arbitrary point far from the write that caused it.

Separately, the currency columns were inconsistent: `accounts.balance_currency`,
`transactions.currency`, and `operations.currency` were `VARCHAR(255)`, while
`authorization_records.currency` was correctly `VARCHAR(3)` (ISO 4217).

---

## Decision

**Every closed-set column gets a `CHECK (col IN (...))` constraint. No
native Postgres `ENUM` type is used anywhere in this schema.** Currency
columns are harmonized to `VARCHAR(3)`.

### Why `CHECK`, not native `ENUM`

The deciding factor is the cost of adding a value — enums are not static in
a payment platform; new payment methods and lifecycle states are a certainty
over the project's life.

| | `VARCHAR` + `CHECK` | Native `ENUM` |
|---|---|---|
| Add a value | `ALTER TABLE t DROP CONSTRAINT ck_x, ADD CONSTRAINT ck_x CHECK (col IN (...))` — one ordinary, transactional statement | `ALTER TYPE t ADD VALUE 'X'` — cannot run inside the same transaction block as other DDL/DML in the same migration, and cannot be rolled back once committed |
| Remove/rename a value | Same `DROP`/`ADD CONSTRAINT` shape | No `DROP VALUE`; requires creating a new type, migrating every dependent column, dropping the old type |
| Flyway/Testcontainers | Identical script runs unmodified in dev, test, and prod | Same script works, but the non-transactional `ADD VALUE` restriction complicates any migration that also touches data using the new value in the same file |
| Hibernate mapping | Plain `VARCHAR` + `@Enumerated(EnumType.STRING)`, already the project-wide pattern | Requires a custom `@JdbcTypeCode`/Hibernate `UserType` per entity to bind a Java enum to the Postgres enum OID — `@Enumerated(STRING)` alone does not do it |

`CHECK` constraints let every entity stay mapped as plain `VARCHAR` with
`@Enumerated(EnumType.STRING)` — no new Hibernate types, no non-transactional
DDL step — and cost one small, ordinary migration per evolution. That is
cheaper than native `ENUM` on the one axis that matters for a schema whose
enums are expected to grow (new `PaymentMethod`, new `TransactionState`,
etc., are on the roadmap).

### The `TransactionState` exception

`transactions.state` and `trx_state_historics.old_state` / `.new_state` are
backed by `TransactionState`, an interface with 11 singleton implementations
(the State pattern — see `domain/model/state/`), **not** a Java `enum`.
`@Enumerated` only targets an actual `enum` type, so it cannot apply here.
These three columns stay mapped as `String` on the entity, exactly as before
— conversion still goes through `TransactionState.fromValue()` / `.name()`
at the repository boundary — and get the same `CHECK` constraint as every
other column. This is the one place the "`@Enumerated(STRING)` everywhere"
rule cannot be applied literally; the DB-level constraint is the enforcement
mechanism there instead of the JPA mapping.

### Currency harmonization

`accounts.balance_currency`, `transactions.currency`, and `operations.currency`
are narrowed from `VARCHAR(255)` to `VARCHAR(3)`, matching
`authorization_records.currency` and ISO 4217. The corresponding entity
fields gain `@Column(length = 3)` so `ddl-auto=validate` keeps agreeing with
the schema.

---

## Procédure d'ajout d'une valeur

1. Add the constant to the Java `enum` (or, for `TransactionState`, add the
   new singleton implementation and its `fromValue()` switch case).
2. New Flyway migration, scoped to that one column:
   ```sql
   ALTER TABLE <table> DROP CONSTRAINT ck_<table>_<column>;
   ALTER TABLE <table> ADD CONSTRAINT ck_<table>_<column>
       CHECK (<column> IN (/* old values */, 'NEW_VALUE'));
   ```
3. `EnumConstraintsDbTest`'s acceptance tests iterate `.values()` for every
   real Java enum (and the fixed `TransactionState` list), so a new constant
   is picked up automatically once step 1 is done — no test code to touch
   for a plain addition. Only extend the fixed list by hand if the new value
   belongs to `TransactionState`.
4. No entity or repository change is needed — the JPA mapping is already
   `@Enumerated(EnumType.STRING)` (or, for `TransactionState`, already
   `String` + `fromValue()`), so it accepts the new constant automatically.

---

## Consequences

### Positive

- A migration, hotfix, or import that writes an out-of-domain value now
  fails at the `INSERT`/`UPDATE`, not at the next read.
- `ddl-auto=validate` and the domain's `@Enumerated(STRING)` convention are
  unaffected — no new Hibernate types introduced.
- Currency columns are now uniform and correctly sized for ISO 4217 codes.

### Negative / Trade-offs

- Adding or removing an enum value now requires a migration in lockstep with
  the Java change (previously the DB accepted anything the app didn't
  reject). This is intentional — see Decision — and the cost is one small,
  ordinary migration.
- `TransactionState`'s three columns remain an documented asymmetry: DB-level
  closed set, but no compile-time `@Enumerated` mapping, because the domain
  type is not an `enum`.

---

## Alternatives Considered

### A — Native Postgres `ENUM` type per column

**Rejected**: non-transactional `ADD VALUE`, no `DROP VALUE`, and a custom
Hibernate type per entity, for no benefit over `CHECK` at this schema's
scale. See comparison table above.

### B — Application-level validation only (status quo)

**Rejected**: this is exactly the gap being closed — a migration, hotfix, or
import bypasses the application layer entirely.

### C — Foreign key to a lookup table per enum

**Rejected**: adds a join and a table per enum for a closed, small,
rarely-changing set of values; `CHECK` gives the same guarantee with none of
the overhead. Would be reconsidered only if a value needed metadata beyond
its name (e.g., a display label), which none of these do today.