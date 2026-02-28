package com.geekersjoel237.koracore.domain.exception;

public class CurrencyMismatchException extends RuntimeException {

    public CurrencyMismatchException(String expected, String actual) {
        super("Currency mismatch: expected " + expected + " but got " + actual);
    }
}