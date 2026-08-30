-- ─────────────────────────────────────────────────────────────────────────────
-- V202608301500 — Enforce closed enumerations and harmonize currency columns
--
-- Every "enum-shaped" column so far was a free VARCHAR(255): the domain
-- enforces a closed set on the way in, but nothing stopped a migration, a
-- hotfix, or a data import from writing a value the domain doesn't recognize.
-- This adds a CHECK constraint per such column, mirroring the closed set
-- already enforced in Java (see ADR-007).
--
-- Postgres validates every existing row when a CHECK or ALTER COLUMN TYPE is
-- added (no NOT VALID used here), so this migration itself is the guarantee
-- that no existing row falls outside the enumeration — it simply fails to
-- apply otherwise.
-- ─────────────────────────────────────────────────────────────────────────────

-- ── Currency harmonization ──────────────────────────────────────────────────
-- authorization_records.currency was already VARCHAR(3); align the other three.

ALTER TABLE accounts     ALTER COLUMN balance_currency TYPE VARCHAR(3);
ALTER TABLE transactions ALTER COLUMN currency         TYPE VARCHAR(3);
ALTER TABLE operations   ALTER COLUMN currency         TYPE VARCHAR(3);

-- ── Auth domain ──────────────────────────────────────────────────────────────

ALTER TABLE users
    ADD CONSTRAINT ck_users_role   CHECK (role IN ('CUSTOMER', 'ADMIN')),
    ADD CONSTRAINT ck_users_status CHECK (status IN ('PENDING', 'VERIFIED', 'SUSPENDED'));

-- ── Account domain ───────────────────────────────────────────────────────────

ALTER TABLE accounts
    ADD CONSTRAINT ck_accounts_resource_type CHECK (resource_type IN ('CUSTOMER_ACCOUNT', 'FLOAT_ACCOUNT'));

-- ── Payment domain ────────────────────────────────────────────────────────────

ALTER TABLE transactions
    ADD CONSTRAINT ck_transactions_state CHECK (state IN (
        'INITIALIZED', 'AUTHORIZED', 'CAPTURED', 'SETTLEMENT_PENDING', 'SETTLED',
        'COMPLETED', 'FAILED', 'AUTHORIZATION_FAILED', 'CAPTURE_FAILED',
        'SETTLEMENT_FAILED', 'REVERSED'
    )),
    ADD CONSTRAINT ck_transactions_type CHECK (type IN ('CASH_IN', 'CASH_OUT', 'P2P_TRANSFER')),
    ADD CONSTRAINT ck_transactions_payment_method CHECK (payment_method IN (
        'CARD', 'ORANGE_MONEY', 'MOBILE_MONEY', 'WALLET'
    ));

ALTER TABLE operations
    ADD CONSTRAINT ck_operations_type CHECK (type IN ('DEBIT', 'CREDIT'));

ALTER TABLE trx_state_historics
    ADD CONSTRAINT ck_trx_state_historics_old_state CHECK (old_state IS NULL OR old_state IN (
        'INITIALIZED', 'AUTHORIZED', 'CAPTURED', 'SETTLEMENT_PENDING', 'SETTLED',
        'COMPLETED', 'FAILED', 'AUTHORIZATION_FAILED', 'CAPTURE_FAILED',
        'SETTLEMENT_FAILED', 'REVERSED'
    )),
    ADD CONSTRAINT ck_trx_state_historics_new_state CHECK (new_state IN (
        'INITIALIZED', 'AUTHORIZED', 'CAPTURED', 'SETTLEMENT_PENDING', 'SETTLED',
        'COMPLETED', 'FAILED', 'AUTHORIZATION_FAILED', 'CAPTURE_FAILED',
        'SETTLEMENT_FAILED', 'REVERSED'
    )),
    ADD CONSTRAINT ck_trx_state_historics_triggered_by CHECK (triggered_by IS NULL OR triggered_by IN (
        'USER_ACTION', 'PROVIDER_CALLBACK', 'SYSTEM_JOB', 'OPERATOR_ACTION'
    ));

-- ── Authorization records ─────────────────────────────────────────────────────

ALTER TABLE authorization_records
    ADD CONSTRAINT ck_authorization_records_status CHECK (status IN (
        'ACTIVE', 'EXPIRED', 'CONSUMED', 'CANCELLED'
    ));