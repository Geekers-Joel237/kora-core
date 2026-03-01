package com.geekersjoel237.koracore.web.api.payment;

import com.geekersjoel237.koracore.domain.model.Transaction;

import java.math.BigDecimal;

public record TransactionResponse(
        String transactionId,
        String transactionNumber,
        String state,
        BigDecimal amount,
        String currency
) {
    public static TransactionResponse from(Transaction tx) {
        var snap = tx.snapshot();
        return new TransactionResponse(
                snap.transactionId().value(),
                snap.transactionNumber(),
                snap.state().name(),
                snap.amount().value(),
                snap.amount().currency()
        );
    }
}