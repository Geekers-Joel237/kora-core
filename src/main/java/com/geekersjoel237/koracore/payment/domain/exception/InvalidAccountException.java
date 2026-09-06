package com.geekersjoel237.koracore.payment.domain.exception;
import com.geekersjoel237.koracore.shared.domain.exception.BusinessException;

public class InvalidAccountException extends BusinessException {

    public InvalidAccountException(String message) {
        super(message);
    }
}