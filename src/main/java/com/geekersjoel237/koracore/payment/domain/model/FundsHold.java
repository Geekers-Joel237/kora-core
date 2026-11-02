package com.geekersjoel237.koracore.payment.domain.model;

import com.geekersjoel237.koracore.payment.domain.enums.HoldStatus;
import com.geekersjoel237.koracore.payment.domain.exception.HoldNotActiveException;
import com.geekersjoel237.koracore.shared.domain.vo.Amount;
import com.geekersjoel237.koracore.shared.domain.vo.Id;

import java.time.Instant;
import java.util.Objects;

public class FundsHold {

    private final Id id;
    private final Id accountId;
    private final Id transactionId;
    private final Amount amount;
    private HoldStatus status;
    private Instant resolvedAt;

    private FundsHold(Id id, Id accountId, Id transactionId, Amount amount,
                      HoldStatus status, Instant resolvedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.accountId = Objects.requireNonNull(accountId, "accountId");
        this.transactionId = Objects.requireNonNull(transactionId, "transactionId");
        this.amount = Objects.requireNonNull(amount, "amount");
        this.status = Objects.requireNonNull(status, "status");
        this.resolvedAt = resolvedAt;
    }

    public static FundsHold place(Id transactionId, Id accountId, Amount amount) {
        if (amount == null || !amount.isStrictPositive())
            throw new IllegalArgumentException("A hold must be for a positive amount");
        return new FundsHold(Id.generate(), accountId, transactionId, amount, HoldStatus.ACTIVE, null);
    }

    public static FundsHold createFromSnapshot(Snapshot snapshot) {
        return new FundsHold(snapshot.id(), snapshot.accountId(), snapshot.transactionId(),
                snapshot.amount(), snapshot.status(), snapshot.resolvedAt());
    }

    public void consume() {
        resolve(HoldStatus.CONSUMED);
    }

    public void release() {
        resolve(HoldStatus.RELEASED);
    }

    private void resolve(HoldStatus outcome) {
        if (status != HoldStatus.ACTIVE)
            throw new HoldNotActiveException(id, status);
        status = outcome;
        resolvedAt = Instant.now();
    }

    public Snapshot snapshot() {
        return new Snapshot(id, accountId, transactionId, amount, status, resolvedAt);
    }

    public record Snapshot(
            Id id,
            Id accountId,
            Id transactionId,
            Amount amount,
            HoldStatus status,
            Instant resolvedAt
    ) {
    }
}
