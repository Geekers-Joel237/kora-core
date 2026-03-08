package com.geekersjoel237.koracore.domain.model.state;

import com.geekersjoel237.koracore.domain.exception.InvalidStateTransitionException;

class PendingState implements TransactionState {

    @Override
    public TransactionState transitionTo(TransactionState next) {
        if (next instanceof CompletedState || next instanceof FailedState) return next;
        throw new InvalidStateTransitionException(this, next);
    }

    @Override
    public String name() {
        return "PENDING";
    }
}