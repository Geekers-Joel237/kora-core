package com.geekersjoel237.koracore.domain.exception;

public class PinValidationException extends RuntimeException {
    public PinValidationException(String message) {
        super(message);
    }
}