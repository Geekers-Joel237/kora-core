package com.geekersjoel237.koracore.payment.domain.model.state;

import com.geekersjoel237.koracore.payment.domain.exception.InvalidStateTransitionException;

class CompletedState implements TransactionState {

    @Override
    public TransactionState transitionTo(TransactionState next) {
        if (next instanceof ReversedState) return next;
        throw new InvalidStateTransitionException(this, next);
    }

    @Override
    public String name() {
        return "COMPLETED";
    }

    @Override
    public boolean isTerminal() {
        return true;
    }
}