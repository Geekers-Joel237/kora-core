package com.geekersjoel237.koracore.payment.application.command.reversePayment;

import com.geekersjoel237.koracore.payment.domain.model.Transaction;
import com.geekersjoel237.koracore.shared.domain.vo.Amount;


public record ReversePaymentResult(
        String transactionId,
        String transactionNumber,
        String state,
        Amount amount
) {


    public static ReversePaymentResult of(Transaction transaction) {
        var snapshot = transaction.snapshot();
        return new ReversePaymentResult(
                snapshot.transactionId().value(),
                snapshot.transactionNumber(),
                snapshot.state().name(),
                snapshot.amount());
    }
}
