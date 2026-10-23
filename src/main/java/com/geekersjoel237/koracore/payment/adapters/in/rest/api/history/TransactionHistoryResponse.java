package com.geekersjoel237.koracore.payment.adapters.in.rest.api.history;

import com.geekersjoel237.koracore.payment.application.query.history.TransactionSummary;
import com.geekersjoel237.koracore.shared.application.cqrs.paging.PageResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

// nextCursor is null on the last page.
public record TransactionHistoryResponse(List<Item> items, String nextCursor) {

    public static TransactionHistoryResponse from(PageResult<TransactionSummary> page) {
        String nextCursor = page.hasNext()
                ? page.lastItem().map(last -> HistoryCursorToken.encode(last.cursor())).orElse(null)
                : null;
        return new TransactionHistoryResponse(page.content().stream().map(Item::from).toList(), nextCursor);
    }

    // amount is signed: negative when money leaves the wallet.
    public record Item(String reference, String kind, String status, BigDecimal amount, String currency,
                       String counterpart, String channel, Instant date) {

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
