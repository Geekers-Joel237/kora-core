package com.geekersjoel237.koracore.auth.domain.exception;
import com.geekersjoel237.koracore.shared.domain.exception.BusinessException;

public class PinValidationException extends BusinessException {
    public PinValidationException(String message) {
        super(message);
    }
}