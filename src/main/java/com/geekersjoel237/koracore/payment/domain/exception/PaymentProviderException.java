package com.geekersjoel237.koracore.payment.domain.exception;
import com.geekersjoel237.koracore.shared.domain.exception.BusinessException;

public class PaymentProviderException extends BusinessException {
    public PaymentProviderException(String message) {
        super(message);
    }
}