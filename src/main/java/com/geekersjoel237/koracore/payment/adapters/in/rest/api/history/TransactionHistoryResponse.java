package com.geekersjoel237.koracore.payment.adapters.in.rest.api.history;

import com.geekersjoel237.koracore.payment.application.query.history.TransactionSummary;
import com.geekersjoel237.koracore.shared.application.cqrs.paging.PageResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record TransactionHistoryResponse(
        List<TransactionItem> transactions,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {

    public static TransactionHistoryResponse from(PageResult<TransactionSummary> result, boolean detail) {
        List<TransactionItem> items = result.content().stream()
                .map(tx -> TransactionItem.from(tx, detail))
                .toList();
        return new TransactionHistoryResponse(
                items,
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages(),
                result.hasNext()
        );
    }

    public record TransactionItem(
            String transactionId,
            String transactionNumber,
            String type,
            String direction,
            String state,
            BigDecimal amount,
            String currency,
            String paymentMethod,
            String counterpart,
            Instant createdAt,
            List<StateEntry> stateHistory
    ) {
        public static TransactionItem from(TransactionSummary tx, boolean detail) {
            List<StateEntry> history = detail
                    ? tx.stateHistory().stream()
                            .map(e -> new StateEntry(e.oldState(), e.newState(), e.occurredAt()))
                            .toList()
                    : null;

            return new TransactionItem(
                    tx.transactionId(),
                    tx.transactionNumber(),
                    tx.type().name(),
                    tx.direction().name(),
                    tx.state(),
                    tx.amount().value(),
                    tx.amount().currency(),
                    tx.paymentMethod().name(),
                    tx.counterpart(),
                    tx.createdAt(),
                    history
            );
        }
    }

    public record StateEntry(String oldState, String newState, Instant occurredAt) {}
}