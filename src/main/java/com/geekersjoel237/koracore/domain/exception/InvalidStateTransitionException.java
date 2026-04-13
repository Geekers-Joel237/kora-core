package com.geekersjoel237.koracore.domain.exception;

import com.geekersjoel237.koracore.domain.model.state.TransactionState;

public class InvalidStateTransitionException extends RuntimeException {

    public InvalidStateTransitionException(TransactionState from, TransactionState to) {
        super("Invalid state transition: " + from + " → " + to);
    }
}