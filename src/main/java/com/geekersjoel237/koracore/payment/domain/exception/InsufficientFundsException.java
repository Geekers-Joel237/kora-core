package com.geekersjoel237.koracore.payment.domain.exception;
import com.geekersjoel237.koracore.shared.domain.exception.BusinessException;

public class InsufficientFundsException extends BusinessException {

    public InsufficientFundsException(String message) {
        super(message);
    }
}