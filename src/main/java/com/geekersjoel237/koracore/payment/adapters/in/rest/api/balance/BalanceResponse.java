package com.geekersjoel237.koracore.payment.adapters.in.rest.api.balance;


import java.math.BigDecimal;
import com.geekersjoel237.koracore.payment.application.query.balance.BalanceResult;

public record BalanceResponse(
        String accountId,
        String accountNumber,
        BigDecimal amount,
        String currency
) {
    public static BalanceResponse from(BalanceResult result) {
        return new BalanceResponse(
                result.accountId(),
                result.accountNumber(),
                result.balance().value(),
                result.balance().currency()
        );
    }
}