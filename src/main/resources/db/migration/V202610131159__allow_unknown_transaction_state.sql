
ALTER TABLE transactions
    DROP CONSTRAINT ck_transactions_state,
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
                         'REVERSED',
                         'UNKNOWN'));

ALTER TABLE trx_state_historics
    DROP CONSTRAINT ck_trx_state_historics_old_state,
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
                             'REVERSED',
                             'UNKNOWN')),
    DROP CONSTRAINT ck_trx_state_historics_new_state,
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
                             'REVERSED',
                             'UNKNOWN'));
