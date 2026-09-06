package com.geekersjoel237.koracore.payment.application.query.history;

import com.geekersjoel237.koracore.payment.domain.enums.Direction;
import com.geekersjoel237.koracore.payment.domain.enums.TransactionType;

import java.time.Instant;


public record TransactionFilter(
        TransactionType type,
        String state,
        Instant from,
        Instant to,
        Direction direction
) {
    public static TransactionFilter empty() {
        return new TransactionFilter(null, null, null, null, null);
    }
}