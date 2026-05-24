package com.geekersjoel237.koracore.domain.exception;

public class TransientPaymentException extends BusinessException {
    public TransientPaymentException(String message, Throwable cause) {
        super(message);
        initCause(cause);
    }
}