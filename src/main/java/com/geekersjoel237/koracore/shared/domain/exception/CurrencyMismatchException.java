package com.geekersjoel237.koracore.shared.domain.exception;

public class CurrencyMismatchException extends BusinessException {

    public CurrencyMismatchException(String expected, String actual) {
        super("Currency mismatch: expected " + expected + " but got " + actual);
    }
}