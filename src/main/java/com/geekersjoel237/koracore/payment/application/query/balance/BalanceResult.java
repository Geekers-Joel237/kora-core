package com.geekersjoel237.koracore.payment.application.query.balance;

import com.geekersjoel237.koracore.shared.domain.vo.Amount;

public record BalanceResult(
        String accountId,
        String accountNumber,
        Amount balance
) {
}
