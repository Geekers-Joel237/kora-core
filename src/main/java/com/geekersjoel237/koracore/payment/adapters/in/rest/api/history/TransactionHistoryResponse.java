package com.geekersjoel237.koracore.payment.adapters.in.rest.api.history;

import com.geekersjoel237.koracore.payment.application.query.history.TransactionKind;
import com.geekersjoel237.koracore.payment.application.query.history.TransactionSummary;
import com.geekersjoel237.koracore.payment.domain.enums.CustomerStatus;
import com.geekersjoel237.koracore.payment.domain.enums.PaymentMethod;
import com.geekersjoel237.koracore.shared.application.cqrs.paging.PageResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static com.geekersjoel237.koracore.shared.adapters.in.rest.ApiSchemas.SIGNED_AMOUNT;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

public record TransactionHistoryResponse(
        @Schema(requiredMode = REQUIRED, description = "Most recent first")
        List<Item> items,
        @Schema(requiredMode = REQUIRED, types = {"string", "null"},
                description = "Opaque: send it back as cursor to read the next page. Null on the last page")
        String nextCursor) {

    public static TransactionHistoryResponse from(PageResult<TransactionSummary> page) {
        String nextCursor = page.hasNext()
                ? page.lastItem().map(last -> HistoryCursorToken.encode(last.cursor())).orElse(null)
                : null;
        return new TransactionHistoryResponse(page.content().stream().map(Item::from).toList(), nextCursor);
    }

    @Schema(name = "TransactionHistoryItem")
    public record Item(
            @Schema(requiredMode = REQUIRED, description = "Human-readable reference")
            String reference,
            @Schema(requiredMode = REQUIRED, implementation = TransactionKind.class)
            String kind,
            @Schema(requiredMode = REQUIRED, implementation = CustomerStatus.class,
                    description = "What the customer sees of the lifecycle")
            String status,
            @Schema(requiredMode = REQUIRED, description = SIGNED_AMOUNT, multipleOf = 1)
            BigDecimal amount,
            @Schema(requiredMode = REQUIRED, description = "ISO 4217 code")
            String currency,
            @Schema(requiredMode = REQUIRED, types = {"string", "null"},
                    description = "The other wallet's phone number, masked by the server. Transfers only, otherwise null")
            String counterpart,
            @Schema(requiredMode = REQUIRED, types = {"string", "null"}, implementation = PaymentMethod.class,
                    description = "Deposits and withdrawals only, otherwise null")
            String channel,
            @Schema(requiredMode = REQUIRED)
            Instant date) {

        static Item from(TransactionSummary summary) {
            return new Item(
                    summary.reference(),
                    summary.kind().name(),
                    summary.status().name(),
                    summary.signedAmount(),
                    summary.amount().currency(),
                    summary.counterpart(),
                    summary.channel() == null ? null : summary.channel().name(),
                    summary.date());
        }
    }
}
