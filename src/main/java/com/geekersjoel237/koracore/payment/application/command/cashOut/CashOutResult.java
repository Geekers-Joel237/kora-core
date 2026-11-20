package com.geekersjoel237.koracore.payment.application.command.cashOut;

import com.geekersjoel237.koracore.payment.domain.model.Transaction;
import com.geekersjoel237.koracore.shared.domain.vo.Amount;


public record CashOutResult(
        String transactionId,
        String transactionNumber,
        String state,
        Amount amount
) {


    public static CashOutResult of(Transaction transaction) {
        var snapshot = transaction.snapshot();
        return new CashOutResult(
                snapshot.transactionId().value(),
                snapshot.transactionNumber(),
                snapshot.state().name(),
                snapshot.amount());
    }
}
