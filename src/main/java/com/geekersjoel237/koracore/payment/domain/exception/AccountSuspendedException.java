package com.geekersjoel237.koracore.payment.domain.exception;
import com.geekersjoel237.koracore.shared.domain.exception.BusinessException;

public class AccountSuspendedException extends BusinessException {
    public AccountSuspendedException(String message) {
        super(message);
    }
}