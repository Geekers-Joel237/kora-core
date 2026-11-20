package com.geekersjoel237.koracore.payment.adapters.in.rest.api.cashOut;

import com.geekersjoel237.koracore.payment.application.command.CashOutCommand;
import com.geekersjoel237.koracore.payment.domain.enums.PaymentMethod;
import com.geekersjoel237.koracore.shared.domain.vo.Amount;
import com.geekersjoel237.koracore.shared.domain.vo.Id;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import com.geekersjoel237.koracore.shared.domain.vo.Pin;

import static com.geekersjoel237.koracore.shared.adapters.in.rest.ApiSchemas.AMOUNT;

public record CashOutRequest(
        @NotBlank(message = "PIN is required")
        String rawPin,

        @Schema(description = AMOUNT, multipleOf = 1)
        @NotNull(message = "Amount is required")
        @DecimalMin(value = "1", message = "Amount must be at least 1 XAF")
        @Digits(integer = 15, fraction = 0,
                message = "XAF has no minor unit: amount must be a whole number")
        BigDecimal amount,

        @NotBlank(message = "Currency is required")
        @Size(min = 3, max = 3, message = "Currency must be a 3-letter ISO code (e.g. XAF)")
        String currency,

        @Schema(description = "One of the listed values, case-insensitive; OM and MOMO are short forms of ORANGE_MONEY and MOBILE_MONEY")
        @NotBlank(message = "Payment method is required")
        String paymentMethod
) {
    public CashOutCommand toCommand(Id customerId, Id correlationId) {
        return new CashOutCommand(correlationId, customerId, Pin.of(rawPin), new Amount(amount, currency),
                PaymentMethod.fromValue(paymentMethod));
    }
}