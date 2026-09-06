package com.geekersjoel237.koracore.payment.adapters.in.rest.api.transfer;

import com.geekersjoel237.koracore.payment.application.command.TransferCommand;
import com.geekersjoel237.koracore.shared.domain.vo.Amount;
import com.geekersjoel237.koracore.shared.domain.vo.Id;
import com.geekersjoel237.koracore.shared.domain.vo.Msisdn;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import com.geekersjoel237.koracore.shared.domain.vo.Pin;

public record TransferRequest(
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

        @NotBlank(message = "Recipient phone number is required")
        String toPhoneNumber
) {
    public TransferCommand toCommand(Id customerId, Id correlationId) {
        return new TransferCommand(correlationId, customerId, Pin.of(rawPin), new Amount(amount, currency), Msisdn.of(toPhoneNumber));
    }
}