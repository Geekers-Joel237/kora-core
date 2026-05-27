package com.geekersjoel237.koracore.domain.exception;

import com.geekersjoel237.koracore.domain.model.state.TransactionState;

public class InvalidStateTransitionException extends BusinessException {

    public InvalidStateTransitionException(TransactionState from, TransactionState to) {
        super("Invalid state transition: " + from + " → " + to);
    }
}