package com.geekersjoel237.koracore.web.api.payment;

import com.geekersjoel237.koracore.application.command.CashOutCommand;
import com.geekersjoel237.koracore.domain.vo.Amount;
import com.geekersjoel237.koracore.domain.vo.Id;

import java.math.BigDecimal;

public record CashOutRequest(String rawPin, BigDecimal amount, String currency, String paymentMethod) {
    public CashOutCommand toCommand(Id customerId) {
        return new CashOutCommand(customerId, rawPin, new Amount(amount, currency), paymentMethod);
    }
}