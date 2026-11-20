package com.geekersjoel237.koracore.payment.application.command.cashIn;

import com.geekersjoel237.koracore.payment.domain.model.Transaction;
import com.geekersjoel237.koracore.shared.domain.vo.Amount;


public record CashInResult(
        String transactionId,
        String transactionNumber,
        String state,
        Amount amount
) {


    public static CashInResult of(Transaction transaction) {
        var snapshot = transaction.snapshot();
        return new CashInResult(
                snapshot.transactionId().value(),
                snapshot.transactionNumber(),
                snapshot.state().name(),
                snapshot.amount());
    }
}
