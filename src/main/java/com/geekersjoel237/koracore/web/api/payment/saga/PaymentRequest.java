package com.geekersjoel237.koracore.web.api.payment.saga;

import com.geekersjoel237.koracore.application.command.PaymentSagaCommand;
import com.geekersjoel237.koracore.domain.vo.Amount;
import com.geekersjoel237.koracore.domain.vo.Id;

import java.math.BigDecimal;

public record PaymentRequest(
        String rawPin,
        BigDecimal amount,
        String currency,
        String paymentMethod,
        String correlationId) {

    public PaymentSagaCommand toCommand(Id customerId) {
        return new PaymentSagaCommand(
                customerId, rawPin, new Amount(amount, currency), paymentMethod, correlationId);
    }
}