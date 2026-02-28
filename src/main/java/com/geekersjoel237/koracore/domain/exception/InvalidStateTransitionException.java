package com.geekersjoel237.koracore.domain.exception;

import com.geekersjoel237.koracore.domain.enums.TransactionState;

public class InvalidStateTransitionException extends RuntimeException {

    public InvalidStateTransitionException(TransactionState from, TransactionState to) {
        super("Invalid state transition: " + from + " → " + to);
    }
}