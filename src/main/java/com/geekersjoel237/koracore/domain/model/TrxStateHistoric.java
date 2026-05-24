package com.geekersjoel237.koracore.domain.model;

import com.geekersjoel237.koracore.domain.enums.TriggerSource;
import com.geekersjoel237.koracore.domain.model.state.TransactionState;
import com.geekersjoel237.koracore.domain.vo.Id;

import java.time.Instant;

public record TrxStateHistoric(
        Id id,
        Id transactionId,
        TransactionState oldState,
        TransactionState newState,
        Instant occurredAt,
        TriggerSource triggeredBy,
        String correlationId,
        String providerRef,
        String actorId,
        String notes
) {

    public TrxStateHistoric {
        if (id == null)            throw new IllegalArgumentException("TrxHistoricState id cannot be null");
        if (transactionId == null) throw new IllegalArgumentException("TrxHistoricState transactionId cannot be null");
        if (newState == null)      throw new IllegalArgumentException("TrxHistoricState newState cannot be null");
        if (occurredAt == null)    throw new IllegalArgumentException("TrxHistoricState occurredAt cannot be null");
        if (triggeredBy == TriggerSource.OPERATOR_ACTION && (notes == null || notes.isBlank()))
            throw new IllegalArgumentException(
                    "Notes are required for OPERATOR_ACTION state transitions");
    }

    public static TrxStateHistoric of(Id transactionId, TransactionState oldState, TransactionState newState) {
        return new TrxStateHistoric(
                Id.generate(), transactionId, oldState, newState, Instant.now(),
                null, null, null, null, null);
    }

    public static TrxStateHistoric of(
            Id transactionId,
            TransactionState oldState,
            TransactionState newState,
            TriggerSource triggeredBy,
            String correlationId,
            String providerRef,
            String actorId,
            String notes) {
        return new TrxStateHistoric(
                Id.generate(), transactionId, oldState, newState, Instant.now(),
                triggeredBy, correlationId, providerRef, actorId, notes);
    }

    public Snapshot snapshot() {
        return new Snapshot(
                id.value(),
                transactionId.value(),
                oldState != null ? oldState.name() : null,
                newState.name(),
                occurredAt,
                triggeredBy,
                correlationId,
                providerRef,
                actorId,
                notes);
    }

    public record Snapshot(
            String id,
            String transactionId,
            String oldState,
            String newState,
            Instant occurredAt,
            TriggerSource triggeredBy,
            String correlationId,
            String providerRef,
            String actorId,
            String notes
    ) {}
}