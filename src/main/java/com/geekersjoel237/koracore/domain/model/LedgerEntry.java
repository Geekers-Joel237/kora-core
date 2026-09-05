package com.geekersjoel237.koracore.domain.model;

import com.geekersjoel237.koracore.domain.enums.LedgerEntryType;
import com.geekersjoel237.koracore.domain.vo.Amount;
import com.geekersjoel237.koracore.domain.vo.Id;

import java.time.Instant;

public class LedgerEntry {

    private final Id entryId;
    private final LedgerEntryType type;
    private final Amount amount;
    private final Id accountId;
    private final Instant createdAt;

    private LedgerEntry(Id entryId, LedgerEntryType type, Amount amount,
                      Id accountId, Instant createdAt) {
        this.entryId = entryId;
        this.type        = type;
        this.amount      = amount;
        this.accountId   = accountId;
        this.createdAt   = createdAt;
    }

    public static LedgerEntry create(Id entryId, LedgerEntryType type,
                                   Amount amount, Id accountId) {
        if (entryId == null) throw new IllegalArgumentException("LedgerEntry id cannot be null");
        if (type == null)        throw new IllegalArgumentException("LedgerEntry type cannot be null");
        if (amount == null)      throw new IllegalArgumentException("LedgerEntry amount cannot be null");
        if (accountId == null)   throw new IllegalArgumentException("LedgerEntry accountId cannot be null");
        return new LedgerEntry(entryId, type, amount, accountId, Instant.now());
    }

    public static LedgerEntry createFromSnapshot(Snapshot snap) {
        return new LedgerEntry(snap.entryId(), snap.type(), snap.amount(),
                snap.accountId(), snap.createdAt());
    }

    public Snapshot snapshot() {
        return new Snapshot(entryId, type, amount, accountId, createdAt);
    }

    public record Snapshot(
            Id entryId,
            LedgerEntryType type,
            Amount amount,
            Id accountId,
            Instant createdAt
    ) {}
}