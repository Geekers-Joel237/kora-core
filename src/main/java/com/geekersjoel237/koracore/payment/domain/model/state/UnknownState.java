package com.geekersjoel237.koracore.payment.domain.model.state;

import com.geekersjoel237.koracore.payment.domain.exception.InvalidStateTransitionException;

public class UnknownState implements TransactionState {
    @Override
    public TransactionState transitionTo(TransactionState next) {

        throw new InvalidStateTransitionException(this, next);
    }

    @Override
    public String name() {
        return "UNKNOWN";
    }

    @Override
    public boolean isTerminal() {
        return false;
    }
}
