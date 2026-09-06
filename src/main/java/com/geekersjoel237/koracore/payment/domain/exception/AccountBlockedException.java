package com.geekersjoel237.koracore.payment.domain.exception;
import com.geekersjoel237.koracore.shared.domain.exception.BusinessException;

public class AccountBlockedException extends BusinessException {
    public AccountBlockedException(String message) {
        super(message);
    }
}