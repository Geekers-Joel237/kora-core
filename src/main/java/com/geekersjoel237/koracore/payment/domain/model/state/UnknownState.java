package com.geekersjoel237.koracore.payment.domain.model.state;

public class UnknownState implements TransactionState {
    @Override
    public TransactionState transitionTo(TransactionState next) {
        if (next instanceof AuthorizedState || next instanceof CapturedState)
            return next;
        throw new IllegalStateException("Unknown state transition");
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
