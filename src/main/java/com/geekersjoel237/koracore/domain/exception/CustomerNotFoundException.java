package com.geekersjoel237.koracore.domain.exception;

public class CustomerNotFoundException extends BusinessException {
    public CustomerNotFoundException(String message) {
        super(message);
    }
}