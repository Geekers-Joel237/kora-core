package com.geekersjoel237.koracore.web.api.payment.balance;

import com.geekersjoel237.koracore.domain.model.Account;

import java.math.BigDecimal;

public record BalanceResponse(
        String accountId,
        String accountNumber,
        BigDecimal amount,
        String currency
) {
    public static BalanceResponse from(Account account) {
        var snap = account.snapshot();
        return new BalanceResponse(
                snap.accountId().value(),
                snap.accountNumber(),
                snap.balance().solde().value(),
                snap.balance().solde().currency()
        );
    }
}