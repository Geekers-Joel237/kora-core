package com.geekersjoel237.koracore.domain.model.state;

public interface TransactionState {

    TransactionState transitionTo(TransactionState next);

    TransactionState INITIALIZED = new InitializedState();
    TransactionState PENDING     = new PendingState();
    TransactionState COMPLETED   = new CompletedState();
    TransactionState FAILED      = new FailedState();

    static TransactionState fromValue(String value){
        return switch (value) {
            case "INITIALIZED" -> INITIALIZED;
            case "PENDING"     -> PENDING;
            case "COMPLETED"   -> COMPLETED;
            case "FAILED"      -> FAILED;
            default -> throw new IllegalArgumentException("Unknown transaction state: " + value);
        };
    }

    String name();
}