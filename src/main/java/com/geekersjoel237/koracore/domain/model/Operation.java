package com.geekersjoel237.koracore.domain.model;

import com.geekersjoel237.koracore.domain.enums.OperationType;
import com.geekersjoel237.koracore.domain.vo.Amount;
import com.geekersjoel237.koracore.domain.vo.Id;

import java.time.Instant;

public class Operation {

    private final Id operationId;
    private final OperationType type;
    private final Amount amount;
    private final Id accountId;
    private final Instant createdAt;

    private Operation(Id operationId, OperationType type, Amount amount,
                      Id accountId, Instant createdAt) {
        this.operationId = operationId;
        this.type        = type;
        this.amount      = amount;
        this.accountId   = accountId;
        this.createdAt   = createdAt;
    }

    public static Operation create(Id operationId, OperationType type,
                                   Amount amount, Id accountId) {
        if (operationId == null) throw new IllegalArgumentException("Operation id cannot be null");
        if (type == null)        throw new IllegalArgumentException("Operation type cannot be null");
        if (amount == null)      throw new IllegalArgumentException("Operation amount cannot be null");
        if (accountId == null)   throw new IllegalArgumentException("Operation accountId cannot be null");
        return new Operation(operationId, type, amount, accountId, Instant.now());
    }

    public static Operation createFromSnapshot(Snapshot snap) {
        return new Operation(snap.operationId(), snap.type(), snap.amount(),
                snap.accountId(), snap.createdAt());
    }

    public Snapshot snapshot() {
        return new Snapshot(operationId, type, amount, accountId, createdAt);
    }

    public record Snapshot(
            Id operationId,
            OperationType type,
            Amount amount,
            Id accountId,
            Instant createdAt
    ) {}
}