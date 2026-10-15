package com.geekersjoel237.koracore.payment.application.query.history;

import com.geekersjoel237.koracore.payment.domain.enums.CustomerStatus;

import java.time.LocalDate;

public record HistoryFilter(CustomerStatus status, TransactionKind kind, LocalDate from, LocalDate to) {

    public HistoryFilter {
        if (from != null && to != null && from.isAfter(to))
            throw new IllegalArgumentException("'from' (" + from + ") cannot be after 'to' (" + to + ")");
    }

    public static HistoryFilter empty() {
        return new HistoryFilter(null, null, null, null);
    }
}
