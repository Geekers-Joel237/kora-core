-- Funds set aside on a customer wallet while a debit awaits the provider (L-1, ADR-008).

CREATE TABLE funds_holds (
    id              VARCHAR(255)             NOT NULL,
    account_id      VARCHAR(255)             NOT NULL,
    transaction_id  VARCHAR(255)             NOT NULL,
    amount          NUMERIC(19,4)            NOT NULL,
    currency        VARCHAR(3)               NOT NULL,
    status          VARCHAR(32)              NOT NULL,
    resolved_at     TIMESTAMP WITH TIME ZONE,
    version         BIGINT                   NOT NULL DEFAULT 0,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    deleted_at      TIMESTAMP WITH TIME ZONE,
    deleted         BOOLEAN                  NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_funds_holds              PRIMARY KEY (id),
    CONSTRAINT fk_funds_holds_accounts     FOREIGN KEY (account_id) REFERENCES accounts (id),
    CONSTRAINT fk_funds_holds_transactions FOREIGN KEY (transaction_id) REFERENCES transactions (id),
    CONSTRAINT ck_funds_holds_amount       CHECK (amount > 0),
    CONSTRAINT ck_funds_holds_status       CHECK (status IN ('ACTIVE',
                                                            'CONSUMED',
                                                            'RELEASED'))
);

CREATE UNIQUE INDEX uq_funds_holds_active_transaction
    ON funds_holds (transaction_id) WHERE status = 'ACTIVE';

CREATE INDEX idx_funds_holds_active_account
    ON funds_holds (account_id) WHERE status = 'ACTIVE';
