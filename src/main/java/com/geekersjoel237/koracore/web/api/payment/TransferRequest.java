package com.geekersjoel237.koracore.web.api.payment;

import com.geekersjoel237.koracore.application.command.TransferCommand;
import com.geekersjoel237.koracore.domain.vo.Amount;
import com.geekersjoel237.koracore.domain.vo.Id;

import java.math.BigDecimal;

public record TransferRequest(
        String rawPin,
        BigDecimal amount,
        String currency,
        String paymentMethod,
        String toPhoneNumber
) {
    public TransferCommand toCommand(Id customerId) {
        return new TransferCommand(customerId, rawPin, new Amount(amount, currency), paymentMethod, toPhoneNumber);
    }
}