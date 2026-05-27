package com.geekersjoel237.koracore.domain.exception;

public class SelfTransferException extends BusinessException {

    public SelfTransferException(String message) {
        super(message);
    }
}