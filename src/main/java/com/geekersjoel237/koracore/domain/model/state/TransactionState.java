package com.geekersjoel237.koracore.domain.model.state;

public interface TransactionState {

    TransactionState transitionTo(TransactionState next);

    TransactionState INITIALIZED = new InitializedState();
    TransactionState PENDING     = new PendingState();
    TransactionState COMPLETED   = new CompletedState();
    TransactionState FAILED      = new FailedState();

    String name();
}