package com.geekersjoel237.koracore.payment.domain.exception;

import com.geekersjoel237.koracore.payment.domain.model.state.TransactionState;
import com.geekersjoel237.koracore.shared.domain.exception.BusinessException;

public class InvalidStateTransitionException extends BusinessException {

    public InvalidStateTransitionException(TransactionState from, TransactionState to) {
        super("Invalid state transition: " + from + " → " + to);
    }
}