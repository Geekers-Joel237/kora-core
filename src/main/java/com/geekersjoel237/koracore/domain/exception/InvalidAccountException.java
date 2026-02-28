package com.geekersjoel237.koracore.domain.exception;

public class InvalidAccountException extends RuntimeException {

    public InvalidAccountException(String message) {
        super(message);
    }
}