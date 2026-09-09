-- -----------------------------------------------------------------------------
-- Closes every enumerated column against values the domain does not define.
--
-- Until now each of these columns was a free VARCHAR(255). The application only
-- ever wrote enum names through @Enumerated(STRING), so the data is clean — but
-- the schema never said so. A correction script, a data import, or a migration
-- written in a hurry could park 'PENDING_REVIEW' in transactions.state, and
-- nothing would complain until TransactionState.fromValue() threw at read time,
-- on a row that is already committed and already counted.
--
-- The code believed in a closed set. The database did not. This aligns them.
--
-- ── Why CHECK and not CREATE TYPE ... AS ENUM ────────────────────────────────
--
-- A native PostgreSQL enum stores a 4-byte OID instead of the string and shows
-- up as a real type in \d. It was rejected on three counts:
--
--   Portability — CHECK is SQL-92 and runs unchanged on any engine. A named
--   enum type is PostgreSQL-only: MySQL's ENUM is a column type, not a reusable
--   one, and Oracle and SQL Server have no equivalent at all. More importantly,
--   Hibernate sends a varchar parameter and PostgreSQL will not cast it, so
--   every mapped field would need @JdbcTypeCode(SqlTypes.NAMED_ENUM) — a
--   dialect bet written into ten entity classes rather than into this directory.
--
--   Reversibility — there is no ALTER TYPE ... DROP VALUE, in any PostgreSQL
--   version. Retiring a state would mean creating a second type, rewriting every
--   column that uses it, and dropping the first. Dropping and re-adding a CHECK
--   is an ordinary statement.
--
--   Uniformity — transactions.state and trx_state_historics.old_state/new_state
--   carry TransactionState, an interface with 11 singletons, not a Java enum.
--   NAMED_ENUM needs an enum, so those three columns — the ones where a bogus
--   value does the most damage — would have needed a converter while the other
--   eight used the annotation. Two mechanisms in one schema.
--
-- ── NULL is not a violation ──────────────────────────────────────────────────
--
-- old_state and triggered_by are nullable. No IS NULL branch is written for
-- them: a CHECK fails only when its expression evaluates to FALSE, and
-- NULL IN (...) evaluates to UNKNOWN. Nullability stays where it belongs, on
-- the NOT NULL clause, and every constraint below reads the same way.
--
-- ── Locking ──────────────────────────────────────────────────────────────────
--
-- ADD CONSTRAINT ... CHECK takes ACCESS EXCLUSIVE and scans the table once to
-- validate the rows already there. That is accepted here for the same reason
-- the vocabulary rename accepted it: Flyway migrates at application startup, so
-- the previous version is already stopped and no traffic is waiting on the
-- lock. Against a live large table the two-step form is required instead — ADD
-- CONSTRAINT ... NOT VALID, commit, then VALIDATE CONSTRAINT, which takes only
-- SHARE UPDATE EXCLUSIVE and lets reads and writes through.
--
-- ── Deliberately not covered ─────────────────────────────────────────────────
--
-- The currency columns stay free VARCHARs. Currency is not an enumeration of
-- this domain: Amount carries it as a String and CurrencyMismatchException
-- exists precisely to arbitrate between several of them. Freezing 'XAF' into
-- the schema would encode a product decision as a data-model one.
--
-- Direction, ProviderOperationType and OtpPurpose are never persisted, so they
-- have no column to constrain.
--
-- ── Adding a value later ─────────────────────────────────────────────────────
--
-- A new enum constant needs a new migration dropping and re-adding the
-- constraint. Forgetting it fails the build: EnumColumnConstraintsDbTest in each
-- module asserts that the literals in the constraint EQUAL the Java values.
-- -----------------------------------------------------------------------------

-- ── Auth ─────────────────────────────────────────────────────────────────────

ALTER TABLE users
    ADD CONSTRAINT ck_users_role
        CHECK (role IN ('CUSTOMER', 'ADMIN'));

ALTER TABLE users
    ADD CONSTRAINT ck_users_status
        CHECK (status IN ('PENDING', 'VERIFIED', 'SUSPENDED'));

-- ── Accounts ─────────────────────────────────────────────────────────────────

ALTER TABLE accounts
    ADD CONSTRAINT ck_accounts_resource_type
        CHECK (resource_type IN ('CUSTOMER_ACCOUNT', 'FLOAT_ACCOUNT'));

-- ── Transactions ─────────────────────────────────────────────────────────────

-- The 11 lifecycle states of ADR-002. This list and the singletons declared on
-- TransactionState are the same set, and the test says so.
ALTER TABLE transactions
    ADD CONSTRAINT ck_transactions_state
        CHECK (state IN ('INITIALIZED',
                         'AUTHORIZED',
                         'CAPTURED',
                         'SETTLEMENT_PENDING',
                         'SETTLED',
                         'COMPLETED',
                         'FAILED',
                         'AUTHORIZATION_FAILED',
                         'CAPTURE_FAILED',
                         'SETTLEMENT_FAILED',
                         'REVERSED'));

ALTER TABLE transactions
    ADD CONSTRAINT ck_transactions_type
        CHECK (type IN ('CASH_IN', 'CASH_OUT', 'P2P_TRANSFER'));

-- Enum names, not the short provider codes. PaymentMethod.value() ('OM',
-- 'MOMO') is a web-layer alias resolved by fromValue(); what reaches the column
-- through @Enumerated(STRING) is always name().
ALTER TABLE transactions
    ADD CONSTRAINT ck_transactions_payment_method
        CHECK (payment_method IN ('CARD', 'ORANGE_MONEY', 'MOBILE_MONEY', 'WALLET'));

-- ── Ledger entries ───────────────────────────────────────────────────────────

-- The double-entry invariant is read with SUM(CASE WHEN type = 'CREDIT' ...).
-- A third value would not break that query — it would fall into the ELSE branch
-- and make an unbalanced transaction look balanced.
ALTER TABLE ledger_entries
    ADD CONSTRAINT ck_ledger_entries_type
        CHECK (type IN ('DEBIT', 'CREDIT'));

-- ── State history ────────────────────────────────────────────────────────────

-- old_state is NULL on a transaction's first transition; see the NULL note in
-- the header.
ALTER TABLE trx_state_historics
    ADD CONSTRAINT ck_trx_state_historics_old_state
        CHECK (old_state IN ('INITIALIZED',
                             'AUTHORIZED',
                             'CAPTURED',
                             'SETTLEMENT_PENDING',
                             'SETTLED',
                             'COMPLETED',
                             'FAILED',
                             'AUTHORIZATION_FAILED',
                             'CAPTURE_FAILED',
                             'SETTLEMENT_FAILED',
                             'REVERSED'));

ALTER TABLE trx_state_historics
    ADD CONSTRAINT ck_trx_state_historics_new_state
        CHECK (new_state IN ('INITIALIZED',
                             'AUTHORIZED',
                             'CAPTURED',
                             'SETTLEMENT_PENDING',
                             'SETTLED',
                             'COMPLETED',
                             'FAILED',
                             'AUTHORIZATION_FAILED',
                             'CAPTURE_FAILED',
                             'SETTLEMENT_FAILED',
                             'REVERSED'));

ALTER TABLE trx_state_historics
    ADD CONSTRAINT ck_trx_state_historics_triggered_by
        CHECK (triggered_by IN ('USER_ACTION', 'PROVIDER_CALLBACK', 'SYSTEM_JOB', 'OPERATOR_ACTION'));

-- ── Authorization records ────────────────────────────────────────────────────

ALTER TABLE authorization_records
    ADD CONSTRAINT ck_authorization_records_status
        CHECK (status IN ('ACTIVE', 'EXPIRED', 'CONSUMED', 'CANCELLED'));
