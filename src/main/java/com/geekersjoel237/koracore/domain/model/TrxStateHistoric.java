package com.geekersjoel237.koracore.domain.model;

import com.geekersjoel237.koracore.domain.model.state.TransactionState;
import com.geekersjoel237.koracore.domain.vo.Id;

import java.time.Instant;

public record TrxStateHistoric(
        Id id,
        Id transactionId,
        TransactionState oldState,
        TransactionState newState,
        Instant occurredAt
) {

    public TrxStateHistoric {
        if (id == null)        throw new IllegalArgumentException("TrxHistoricState id cannot be null");
        if (transactionId == null)     throw new IllegalArgumentException("TrxHistoricState transactionId cannot be null");
        if (newState == null)  throw new IllegalArgumentException("TrxHistoricState newState cannot be null");
        if (occurredAt == null) throw new IllegalArgumentException("TrxHistoricState occurredAt cannot be null");
    }

    public static TrxStateHistoric of(Id transactionId, TransactionState oldState, TransactionState newState) {
        return new TrxStateHistoric(Id.generate(), transactionId, oldState, newState, Instant.now());
    }

    public Snapshot snapshot() {
        return new Snapshot(id.value(), transactionId.value(),
                oldState != null ? oldState.name() : null,
                newState.name(), occurredAt);
    }

    public record Snapshot(
            String id,
            String transactionId,
            String oldState,
            String newState,
            Instant occurredAt
    ){}
}