package com.geekersjoel237.koracore.payment.application.query.history;

import com.geekersjoel237.koracore.shared.domain.vo.Id;

import java.time.Instant;

public record HistoryCursor(Instant occurredAt, Id transactionId) {

    public HistoryCursor {
        if (occurredAt == null || transactionId == null)
            throw new IllegalArgumentException("A history cursor needs both an instant and a transaction id");
    }
}
