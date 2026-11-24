package com.geekersjoel237.koracore.payment.domain.exception;

public class PaymentTimeoutProviderException extends PaymentProviderException {
    public PaymentTimeoutProviderException(String message) {
        super(message);
    }

    @Override
    public String code() {
        return "PROVIDER_TIMEOUT";
    }
}
