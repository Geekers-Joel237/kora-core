package com.geekersjoel237.koracore.web.api.payment.cashOut;

import com.geekersjoel237.koracore.application.command.CashOutCommand;
import com.geekersjoel237.koracore.domain.enums.PaymentMethod;
import com.geekersjoel237.koracore.domain.vo.Amount;
import com.geekersjoel237.koracore.domain.vo.Id;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CashOutRequest(
        @NotBlank(message = "PIN is required")
        String rawPin,

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
        BigDecimal amount,

        @NotBlank(message = "Currency is required")
        @Size(min = 3, max = 3, message = "Currency must be a 3-letter ISO code (e.g. XAF)")
        String currency,

        @NotBlank(message = "Payment method is required")
        String paymentMethod
) {
    public CashOutCommand toCommand(Id customerId) {
        return new CashOutCommand(customerId, rawPin, new Amount(amount, currency),
                PaymentMethod.fromValue(paymentMethod));
    }
}