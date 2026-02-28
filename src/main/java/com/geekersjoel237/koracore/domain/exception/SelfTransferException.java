package com.geekersjoel237.koracore.domain.exception;

public class SelfTransferException extends RuntimeException {

    public SelfTransferException(String message) {
        super(message);
    }
}