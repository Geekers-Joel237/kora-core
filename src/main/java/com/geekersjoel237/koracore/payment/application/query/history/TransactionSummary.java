package com.geekersjoel237.koracore.payment.application.query.history;

import com.geekersjoel237.koracore.payment.domain.enums.CustomerStatus;
import com.geekersjoel237.koracore.payment.domain.enums.PaymentMethod;
import com.geekersjoel237.koracore.shared.domain.vo.Amount;
import com.geekersjoel237.koracore.shared.domain.vo.Id;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionSummary(
        Id transactionId,
        String reference,
        TransactionKind kind,
        CustomerStatus status,
        Amount amount,
        PaymentMethod channel,
        String counterpart,
        Instant date
) {

    public BigDecimal signedAmount() {
        return kind.leavesTheWallet() ? amount.value().negate() : amount.value();
    }

    public HistoryCursor cursor() {
        return new HistoryCursor(date, transactionId);
    }
}
