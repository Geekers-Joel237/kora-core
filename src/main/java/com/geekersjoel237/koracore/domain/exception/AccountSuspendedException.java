package com.geekersjoel237.koracore.domain.exception;

public class AccountSuspendedException extends RuntimeException {
    public AccountSuspendedException(String message) {
        super(message);
    }
}