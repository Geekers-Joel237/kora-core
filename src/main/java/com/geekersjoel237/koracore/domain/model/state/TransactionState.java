package com.geekersjoel237.koracore.domain.model.state;

public interface TransactionState {

    TransactionState INITIALIZED = new InitializedState();
    TransactionState AUTHORIZED = new AuthorizedState();
    TransactionState CAPTURED = new CapturedState();
    TransactionState SETTLEMENT_PENDING = new SettlementPendingState();
    TransactionState SETTLED = new SettledState();
    TransactionState COMPLETED = new CompletedState();
    TransactionState FAILED = new FailedState();
    TransactionState AUTHORIZATION_FAILED = new AuthorizationFailedState();
    TransactionState CAPTURE_FAILED = new CaptureFailedState();
    TransactionState SETTLEMENT_FAILED = new SettlementFailedState();
    TransactionState REVERSED = new ReversedState();

    static TransactionState fromValue(String value) {
        return switch (value) {
            case "INITIALIZED" -> INITIALIZED;
            case "AUTHORIZED" -> AUTHORIZED;
            case "CAPTURED" -> CAPTURED;
            case "SETTLEMENT_PENDING" -> SETTLEMENT_PENDING;
            case "SETTLED" -> SETTLED;
            case "COMPLETED" -> COMPLETED;
            case "FAILED" -> FAILED;
            case "AUTHORIZATION_FAILED" -> AUTHORIZATION_FAILED;
            case "CAPTURE_FAILED" -> CAPTURE_FAILED;
            case "SETTLEMENT_FAILED" -> SETTLEMENT_FAILED;
            case "REVERSED" -> REVERSED;
            default -> throw new IllegalArgumentException("Unknown transaction state: " + value);
        };
    }

    TransactionState transitionTo(TransactionState next);

    String name();

    boolean isTerminal();
}