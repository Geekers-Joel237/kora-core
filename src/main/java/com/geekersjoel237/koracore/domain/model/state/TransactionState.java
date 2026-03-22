package com.geekersjoel237.koracore.domain.model.state;

public interface TransactionState {

    TransactionState transitionTo(TransactionState next);

    TransactionState INITIALIZED        = new InitializedState();
    TransactionState AUTHORIZED         = new AuthorizedState();
    TransactionState CAPTURED           = new CapturedState();
    TransactionState SETTLEMENT_PENDING = new SettlementPendingState();
    TransactionState SETTLED            = new SettledState();
    TransactionState COMPLETED          = new CompletedState();
    TransactionState FAILED             = new FailedState();

    static TransactionState fromValue(String value) {
        return switch (value) {
            case "INITIALIZED"        -> INITIALIZED;
            case "AUTHORIZED"         -> AUTHORIZED;
            case "CAPTURED"           -> CAPTURED;
            case "SETTLEMENT_PENDING" -> SETTLEMENT_PENDING;
            case "SETTLED"            -> SETTLED;
            case "COMPLETED"          -> COMPLETED;
            case "FAILED"             -> FAILED;
            default -> throw new IllegalArgumentException("Unknown transaction state: " + value);
        };
    }

    String name();
}