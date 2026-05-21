package com.geekersjoel237.koracore.domain.exception;

public class OtpExpiredException extends BusinessException {
    public OtpExpiredException(String message) {
        super(message);
    }
}