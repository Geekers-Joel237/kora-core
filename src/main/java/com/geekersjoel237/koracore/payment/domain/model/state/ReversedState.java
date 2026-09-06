package com.geekersjoel237.koracore.payment.domain.model.state;

import com.geekersjoel237.koracore.payment.domain.exception.InvalidStateTransitionException;

class ReversedState implements TransactionState {

    @Override
    public TransactionState transitionTo(TransactionState next) {
        throw new InvalidStateTransitionException(this, next);
    }

    @Override
    public String name() {
        return "REVERSED";
    }

    @Override
    public boolean isTerminal() {
        return true;
    }
}