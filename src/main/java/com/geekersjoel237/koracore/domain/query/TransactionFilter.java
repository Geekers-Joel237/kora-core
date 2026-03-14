package com.geekersjoel237.koracore.domain.query;

import com.geekersjoel237.koracore.domain.enums.Direction;
import com.geekersjoel237.koracore.domain.enums.TransactionType;

import java.time.Instant;

/**
 * Optional filter criteria for transaction history queries.
 * All fields are nullable — null means "no restriction on this criterion".
 */
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