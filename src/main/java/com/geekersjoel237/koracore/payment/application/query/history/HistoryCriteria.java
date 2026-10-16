package com.geekersjoel237.koracore.payment.application.query.history;

import com.geekersjoel237.koracore.payment.domain.model.state.TransactionState;

import java.time.Instant;
import java.util.Set;

public record HistoryCriteria(Set<TransactionState> states, TransactionKind kind,
                              Instant from, Instant until, HistoryCursor after) {

    public HistoryCriteria {
        states = states == null ? Set.of() : Set.copyOf(states);
    }
}
