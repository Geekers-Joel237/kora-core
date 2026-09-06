package com.geekersjoel237.koracore.payment.adapters.in.rest.api.shared;


import java.math.BigDecimal;
import com.geekersjoel237.koracore.payment.application.result.PaymentResult;

public record TransactionResponse(
        String transactionId,
        String transactionNumber,
        String state,
        BigDecimal amount,
        String currency
) {
    public static TransactionResponse from(PaymentResult result) {
        return new TransactionResponse(
                result.transactionId(),
                result.transactionNumber(),
                result.state(),
                result.amount().value(),
                result.amount().currency()
        );
    }
}