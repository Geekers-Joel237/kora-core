package com.geekersjoel237.koracore.payment.domain.exception;
import com.geekersjoel237.koracore.shared.domain.exception.BusinessException;

public class ProviderException extends BusinessException {
    public ProviderException(String message) {
        super(message);
    }
}