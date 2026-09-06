package com.geekersjoel237.koracore.auth.domain.exception;
import com.geekersjoel237.koracore.shared.domain.exception.BusinessException;

public class DuplicateEmailException extends BusinessException {
    public DuplicateEmailException(String message) {
        super(message);
    }
}