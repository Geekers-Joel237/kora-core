package com.geekersjoel237.koracore.payment.adapters.in.rest.api.cashOut;

import com.geekersjoel237.koracore.payment.application.command.CashOutCommand;
import com.geekersjoel237.koracore.payment.domain.enums.PaymentMethod;
import com.geekersjoel237.koracore.shared.domain.vo.Amount;
import com.geekersjoel237.koracore.shared.domain.vo.Id;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import com.geekersjoel237.koracore.shared.domain.vo.Pin;

public record CashOutRequest(
        @NotBlank(message = "PIN is required")
        String rawPin,

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "1", message = "Amount must be at least 1 XAF")
        @Digits(integer = 15, fraction = 0,
                message = "XAF has no minor unit: amount must be a whole number")
        BigDecimal amount,

        @NotBlank(message = "Currency is required")
        @Size(min = 3, max = 3, message = "Currency must be a 3-letter ISO code (e.g. XAF)")
        String currency,

        @NotBlank(message = "Payment method is required")
        String paymentMethod
) {
    public CashOutCommand toCommand(Id customerId, Id correlationId) {
        return new CashOutCommand(correlationId, customerId, Pin.of(rawPin), new Amount(amount, currency),
                PaymentMethod.fromValue(paymentMethod));
    }
}