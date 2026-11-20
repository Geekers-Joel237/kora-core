package com.geekersjoel237.koracore.payment.application.command.transfer;

import com.geekersjoel237.koracore.payment.domain.model.Transaction;
import com.geekersjoel237.koracore.shared.domain.vo.Amount;


public record TransferResult(
        String transactionId,
        String transactionNumber,
        String state,
        Amount amount
) {


    public static TransferResult of(Transaction transaction) {
        var snapshot = transaction.snapshot();
        return new TransferResult(
                snapshot.transactionId().value(),
                snapshot.transactionNumber(),
                snapshot.state().name(),
                snapshot.amount());
    }
}
