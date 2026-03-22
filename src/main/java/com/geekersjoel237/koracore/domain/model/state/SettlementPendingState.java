package com.geekersjoel237.koracore.domain.model.state;

import com.geekersjoel237.koracore.domain.exception.InvalidStateTransitionException;

class SettlementPendingState implements TransactionState {

    @Override
    public TransactionState transitionTo(TransactionState next) {
        if (next instanceof SettledState || next instanceof SettlementFailedState) return next;
        throw new InvalidStateTransitionException(this, next);
    }

    @Override
    public String name() {
        return "SETTLEMENT_PENDING";
    }

    @Override
    public boolean isTerminal() {
        return false;
    }
}