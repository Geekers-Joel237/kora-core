package com.geekersjoel237.koracore.payment.adapters.in.rest.api.shared;

import com.geekersjoel237.koracore.payment.application.command.cashIn.CashInResult;
import com.geekersjoel237.koracore.payment.application.command.cashOut.CashOutResult;
import com.geekersjoel237.koracore.payment.application.command.reversePayment.ReversePaymentResult;
import com.geekersjoel237.koracore.payment.application.command.transfer.TransferResult;
import com.geekersjoel237.koracore.shared.domain.vo.Amount;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

import static com.geekersjoel237.koracore.shared.adapters.in.rest.ApiSchemas.AMOUNT;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

public record TransactionResponse(
        @Schema(requiredMode = REQUIRED)
        String transactionId,
        @Schema(requiredMode = REQUIRED, description = "Human-readable reference to show the customer")
        String transactionNumber,
        @Schema(requiredMode = REQUIRED, description = "Technical lifecycle state; UNKNOWN when the provider outcome is not known yet")
        String state,
        @Schema(requiredMode = REQUIRED, description = AMOUNT, multipleOf = 1)
        BigDecimal amount,
        @Schema(requiredMode = REQUIRED, description = "ISO 4217 code")
        String currency
) {
    public static TransactionResponse from(CashInResult result) {
        return of(result.transactionId(), result.transactionNumber(), result.state(), result.amount());
    }

    public static TransactionResponse from(CashOutResult result) {
        return of(result.transactionId(), result.transactionNumber(), result.state(), result.amount());
    }

    public static TransactionResponse from(TransferResult result) {
        return of(result.transactionId(), result.transactionNumber(), result.state(), result.amount());
    }

    public static TransactionResponse from(ReversePaymentResult result) {
        return of(result.transactionId(), result.transactionNumber(), result.state(), result.amount());
    }

    private static TransactionResponse of(String transactionId, String transactionNumber, String state, Amount amount) {
        return new TransactionResponse(transactionId, transactionNumber, state, amount.value(), amount.currency());
    }
}
