package com.geekersjoel237.koracore.auth.domain.exception;
import com.geekersjoel237.koracore.shared.domain.exception.BusinessException;

public class InvalidOtpException extends BusinessException {
    public InvalidOtpException(String message) {
        super(message);
    }
}