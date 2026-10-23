package com.geekersjoel237.koracore.shared.domain.exception;

/**
 * Thrown inside {@link com.geekersjoel237.koracore.shared.ports.out.transaction.TransactionBoundary#execute},
 * rolls the transaction back — the only way a use case aborts.
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}
