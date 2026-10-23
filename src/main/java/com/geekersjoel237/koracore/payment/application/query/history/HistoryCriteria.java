package com.geekersjoel237.koracore.payment.application.query.history;

import com.geekersjoel237.koracore.payment.domain.model.state.TransactionState;

import java.time.Instant;
import java.util.Set;

// The window is half-open: from inclusive, until exclusive. An empty state set means any state.
public record HistoryCriteria(Set<TransactionState> states, TransactionKind kind,
                              Instant from, Instant until, HistoryCursor after) {

    public HistoryCriteria {
        states = states == null ? Set.of() : Set.copyOf(states);
    }

    public static HistoryCriteria everything() {
        return new HistoryCriteria(Set.of(), null, null, null, null);
    }

    public HistoryCriteria continuingAfter(HistoryCursor cursor) {
        return new HistoryCriteria(states, kind, from, until, cursor);
    }
}
