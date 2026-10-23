package com.geekersjoel237.koracore.shared.application.transaction;


public class RetryExhaustedException extends RuntimeException {

    public RetryExhaustedException(String message, Throwable cause) {
        super(message, cause);
    }
}
