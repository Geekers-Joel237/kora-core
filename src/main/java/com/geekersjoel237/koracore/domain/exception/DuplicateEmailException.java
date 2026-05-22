package com.geekersjoel237.koracore.domain.exception;

public class DuplicateEmailException extends BusinessException {
    public DuplicateEmailException(String message) {
        super(message);
    }
}