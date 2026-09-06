package com.geekersjoel237.koracore.auth.domain.exception;
import com.geekersjoel237.koracore.shared.domain.exception.BusinessException;

public class CustomerNotFoundException extends BusinessException {
    public CustomerNotFoundException(String message) {
        super(message);
    }
}