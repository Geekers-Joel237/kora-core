-- ─────────────────────────────────────────────────────────────────────────────
-- V1 — Initial schema  (Step 1 baseline)
--
-- Tables (dependency order):
--   users → customers
--   ledgers
--   accounts
--   transactions → operations
--                → trx_state_historics
--   authorization_records
--
-- All tables inherit from BaseEntity:
--   id, created_at, updated_at, deleted_at, deleted (soft-delete flag)
-- VersionedEntity adds: version (optimistic lock)
-- ─────────────────────────────────────────────────────────────────────────────

-- ── Auth domain ───────────────────────────────────────────────────────────────

CREATE TABLE users (
    id          VARCHAR(255) NOT NULL,
    full_name   VARCHAR(255) NOT NULL,
    email       VARCHAR(255) NOT NULL,
    role        VARCHAR(255) NOT NULL,
    status      VARCHAR(255) NOT NULL,
    version     BIGINT       NOT NULL DEFAULT 0,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    deleted_at  TIMESTAMP WITH TIME ZONE,
    deleted     BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_email UNIQUE (email)
);

CREATE TABLE customers (
    id           VARCHAR(255) NOT NULL,
    phone_prefix VARCHAR(255) NOT NULL,
    phone_number VARCHAR(255) NOT NULL,
    hashed_pin   VARCHAR(255) NOT NULL,
    version      BIGINT       NOT NULL DEFAULT 0,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    deleted_at   TIMESTAMP WITH TIME ZONE,
    deleted      BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_customers               PRIMARY KEY (id),
    CONSTRAINT uq_customers_phone         UNIQUE (phone_prefix, phone_number),
    CONSTRAINT fk_customers_users         FOREIGN KEY (id) REFERENCES users (id)
);

-- ── Ledger domain ─────────────────────────────────────────────────────────────

CREATE TABLE ledgers (
    id         VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    deleted_at TIMESTAMP WITH TIME ZONE,
    deleted    BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_ledgers PRIMARY KEY (id)
);

-- ── Account domain ────────────────────────────────────────────────────────────

CREATE TABLE accounts (
    id               VARCHAR(255)  NOT NULL,
    account_number   VARCHAR(255)  NOT NULL,
    resource_type    VARCHAR(255)  NOT NULL,
    resource_id      VARCHAR(255)  NOT NULL,
    balance_amount   NUMERIC(19,4) NOT NULL,
    balance_currency VARCHAR(255)  NOT NULL,
    is_blocked       BOOLEAN       NOT NULL DEFAULT FALSE,
    version          BIGINT        NOT NULL DEFAULT 0,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    deleted_at       TIMESTAMP WITH TIME ZONE,
    deleted          BOOLEAN       NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_accounts          PRIMARY KEY (id),
    CONSTRAINT uq_accounts_number   UNIQUE (account_number),
    CONSTRAINT uq_accounts_resource UNIQUE (resource_type, resource_id)
);

CREATE INDEX idx_accounts_resource_id    ON accounts (resource_id);
CREATE INDEX idx_accounts_resource_type  ON accounts (resource_type);
CREATE INDEX idx_accounts_account_number ON accounts (account_number);

-- ── Payment domain ────────────────────────────────────────────────────────────

CREATE TABLE transactions (
    id                 VARCHAR(255)  NOT NULL,
    transaction_number VARCHAR(255)  NOT NULL,
    from_id            VARCHAR(255)  NOT NULL,
    to_id              VARCHAR(255)  NOT NULL,
    state              VARCHAR(255)  NOT NULL,
    type               VARCHAR(255)  NOT NULL,
    payment_method     VARCHAR(255)  NOT NULL,
    amount             NUMERIC(19,4) NOT NULL,
    currency           VARCHAR(255)  NOT NULL,
    occurred_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    version            BIGINT        NOT NULL DEFAULT 0,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    deleted_at         TIMESTAMP WITH TIME ZONE,
    deleted            BOOLEAN       NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_transactions        PRIMARY KEY (id),
    CONSTRAINT uq_transactions_number UNIQUE (transaction_number)
);

CREATE INDEX idx_transactions_from_id ON transactions (from_id);
CREATE INDEX idx_transactions_to_id   ON transactions (to_id);

CREATE TABLE operations (
    id             VARCHAR(255)  NOT NULL,
    transaction_id VARCHAR(255)  NOT NULL,
    type           VARCHAR(255)  NOT NULL,
    amount         NUMERIC(19,4) NOT NULL,
    currency       VARCHAR(255)  NOT NULL,
    account_id     VARCHAR(255)  NOT NULL,
    occurred_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    deleted_at     TIMESTAMP WITH TIME ZONE,
    deleted        BOOLEAN       NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_operations PRIMARY KEY (id)
);

CREATE INDEX idx_operations_transaction_id ON operations (transaction_id);
CREATE INDEX idx_operations_account_id     ON operations (account_id);
CREATE INDEX idx_operations_type           ON operations (type);

CREATE TABLE trx_state_historics (
    id             VARCHAR(255) NOT NULL,
    transaction_id VARCHAR(255) NOT NULL,
    old_state      VARCHAR(255),
    new_state      VARCHAR(255) NOT NULL,
    occurred_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    triggered_by   VARCHAR(255),
    correlation_id VARCHAR(255),
    provider_ref   VARCHAR(255),
    actor_id       VARCHAR(255),
    notes          VARCHAR(255),
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    deleted_at     TIMESTAMP WITH TIME ZONE,
    deleted        BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_trx_state_historics PRIMARY KEY (id)
);

CREATE INDEX idx_trx_state_historics_transaction_id ON trx_state_historics (transaction_id);

-- ── Authorization records ─────────────────────────────────────────────────────

CREATE TABLE authorization_records (
    id                 VARCHAR(255)  NOT NULL,
    transaction_id     VARCHAR(255)  NOT NULL,
    provider_reference VARCHAR(255)  NOT NULL,
    authorized_amount  NUMERIC(19,4) NOT NULL,
    currency           VARCHAR(3)    NOT NULL,
    authorized_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    status             VARCHAR(32)   NOT NULL,
    version            BIGINT        NOT NULL DEFAULT 0,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    deleted_at         TIMESTAMP WITH TIME ZONE,
    deleted            BOOLEAN       NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_authorization_records PRIMARY KEY (id)
);

CREATE INDEX idx_auth_records_transaction_id ON authorization_records (transaction_id);
CREATE INDEX idx_auth_records_expires_at     ON authorization_records (expires_at);
