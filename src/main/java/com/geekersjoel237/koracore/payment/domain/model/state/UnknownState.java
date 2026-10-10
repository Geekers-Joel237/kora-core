package com.geekersjoel237.koracore.payment.domain.model.state;

/**
 * Created on 09/09/2026
 *
 * @author Geekers_Joel237
 **/
public class UnknownState implements TransactionState {
    @Override
    public TransactionState transitionTo(TransactionState next) {
        return next;
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
