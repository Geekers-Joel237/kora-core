package com.geekersjoel237.koracore.payment.domain.model.state;

import com.geekersjoel237.koracore.payment.domain.exception.InvalidStateTransitionException;

class AuthorizationFailedState implements TransactionState {

    @Override
    public TransactionState transitionTo(TransactionState next) {
        throw new InvalidStateTransitionException(this, next);
    }

    @Override
    public String name() {
        return "AUTHORIZATION_FAILED";
    }

    @Override
    public boolean isTerminal() {
        return true;
    }
}