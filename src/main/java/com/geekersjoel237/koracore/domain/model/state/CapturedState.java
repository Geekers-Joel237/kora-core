package com.geekersjoel237.koracore.domain.model.state;

import com.geekersjoel237.koracore.domain.exception.InvalidStateTransitionException;

class CapturedState implements TransactionState {

    @Override
    public TransactionState transitionTo(TransactionState next) {
        if (next instanceof SettlementPendingState
                || next instanceof CaptureFailedState
                || next instanceof ReversedState) return next;
        throw new InvalidStateTransitionException(this, next);
    }

    @Override
    public String name() {
        return "CAPTURED";
    }

    @Override
    public boolean isTerminal() {
        return false;
    }
}