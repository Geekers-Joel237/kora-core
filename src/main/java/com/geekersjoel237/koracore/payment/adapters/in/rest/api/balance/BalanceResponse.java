package com.geekersjoel237.koracore.payment.adapters.in.rest.api.balance;

import com.geekersjoel237.koracore.payment.application.query.balance.BalanceResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

import static com.geekersjoel237.koracore.shared.adapters.in.rest.ApiSchemas.AMOUNT;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

public record BalanceResponse(
        @Schema(requiredMode = REQUIRED)
        String accountId,
        @Schema(requiredMode = REQUIRED)
        String accountNumber,
        @Schema(requiredMode = REQUIRED, multipleOf = 1,
                description = "Available balance, the one to show and the most the customer can spend: "
                              + "the ledger balance minus the funds held by debits awaiting the provider. " + AMOUNT)
        BigDecimal amount,
        @Schema(requiredMode = REQUIRED, multipleOf = 1, description = "Ledger balance. " + AMOUNT)
        BigDecimal ledgerAmount,
        @Schema(requiredMode = REQUIRED, description = "ISO 4217 code")
        String currency
) {
    public static BalanceResponse from(BalanceResult result) {
        return new BalanceResponse(
                result.accountId(),
                result.accountNumber(),
                result.available().value(),
                result.balance().value(),
                result.balance().currency()
        );
    }
}
