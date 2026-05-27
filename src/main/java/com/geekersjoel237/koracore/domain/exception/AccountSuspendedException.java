package com.geekersjoel237.koracore.domain.exception;

public class AccountSuspendedException extends BusinessException {
    public AccountSuspendedException(String message) {
        super(message);
    }
}