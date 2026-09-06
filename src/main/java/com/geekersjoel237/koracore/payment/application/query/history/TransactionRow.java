package com.geekersjoel237.koracore.payment.application.query.history;

import com.geekersjoel237.koracore.payment.domain.enums.PaymentMethod;
import com.geekersjoel237.koracore.payment.domain.enums.TransactionType;
import com.geekersjoel237.koracore.shared.domain.vo.Amount;

import java.time.Instant;
import java.util.List;


public record TransactionRow(
        String transactionId,
        String transactionNumber,
        TransactionType type,
        String state,
        Amount amount,
        PaymentMethod paymentMethod,
        String fromAccountId,
        String toAccountId,
        String counterpartPhonePrefix,
        String counterpartPhoneNumber,
        Instant createdAt,
        List<TransactionSummary.StateEntry> stateHistory
) {
}
