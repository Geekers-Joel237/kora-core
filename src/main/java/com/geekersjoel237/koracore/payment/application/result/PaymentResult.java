package com.geekersjoel237.koracore.payment.application.result;

import com.geekersjoel237.koracore.payment.domain.model.Transaction;
import com.geekersjoel237.koracore.shared.domain.vo.Amount;


public record PaymentResult(
        String transactionId,
        String transactionNumber,
        String state,
        Amount amount
) {


    public static PaymentResult of(Transaction transaction) {
        var snapshot = transaction.snapshot();
        return new PaymentResult(
                snapshot.transactionId().value(),
                snapshot.transactionNumber(),
                snapshot.state().name(),
                snapshot.amount());
    }
}
