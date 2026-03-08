package com.geekersjoel237.koracore.domain.model.state;

import com.geekersjoel237.koracore.domain.exception.InvalidStateTransitionException;

class InitializedState implements TransactionState {

    @Override
    public TransactionState transitionTo(TransactionState next) {
        if (next instanceof PendingState) return next;
        throw new InvalidStateTransitionException(this, next);
    }

    @Override
    public String name() {
        return "INITIALIZED";
    }
}