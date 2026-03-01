package com.geekersjoel237.koracore.web.api.payment;

import com.geekersjoel237.koracore.application.command.CashInCommand;
import com.geekersjoel237.koracore.domain.vo.Amount;
import com.geekersjoel237.koracore.domain.vo.Id;

import java.math.BigDecimal;

public record CashInRequest(String rawPin, BigDecimal amount, String currency, String paymentMethod) {
    public CashInCommand toCommand(Id customerId) {
        return new CashInCommand(customerId, rawPin, new Amount(amount, currency), paymentMethod);
    }
}