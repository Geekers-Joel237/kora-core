package com.geekersjoel237.koracore.auth.domain.exception;
import com.geekersjoel237.koracore.shared.domain.exception.BusinessException;

public class OtpExpiredException extends BusinessException {
    public OtpExpiredException(String message) {
        super(message);
    }
}