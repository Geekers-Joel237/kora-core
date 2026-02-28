package com.geekersjoel237.koracore.domain.model.state;

import com.geekersjoel237.koracore.domain.exception.InvalidStateTransitionException;

class CompletedState implements TransactionState {

    @Override
    public TransactionState transitionTo(TransactionState next) {
        throw new InvalidStateTransitionException(this, next);
    }


    @Override
    public String name() {
        return "COMPLETED";
    }
}