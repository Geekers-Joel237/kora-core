package com.geekersjoel237.koracore.web.api.payment.authorize;

import com.geekersjoel237.koracore.application.command.AuthorizePaymentCommand;
import com.geekersjoel237.koracore.domain.vo.Amount;
import com.geekersjoel237.koracore.domain.vo.Id;

import java.math.BigDecimal;

public record AuthorizeRequest(
        String rawPin,
        BigDecimal amount,
        String currency,
        String paymentMethod,
        String correlationId) {

    public AuthorizePaymentCommand toCommand(Id customerId) {
        return new AuthorizePaymentCommand(
                customerId, rawPin, new Amount(amount, currency), paymentMethod, correlationId);
    }
}