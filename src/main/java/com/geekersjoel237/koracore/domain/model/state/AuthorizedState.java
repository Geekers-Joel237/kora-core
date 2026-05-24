package com.geekersjoel237.koracore.domain.model.state;

import com.geekersjoel237.koracore.domain.exception.InvalidStateTransitionException;

class AuthorizedState implements TransactionState {

    @Override
    public TransactionState transitionTo(TransactionState next) {
        if (next instanceof CapturedState
                || next instanceof FailedState
                || next instanceof AuthorizationFailedState
                || next instanceof CaptureFailedState
                || next instanceof ReversedState) return next;
        throw new InvalidStateTransitionException(this, next);
    }

    @Override
    public String name() {
        return "AUTHORIZED";
    }

    @Override
    public boolean isTerminal() {
        return false;
    }
}