package com.geekersjoel237.koracore.domain.model;

import com.geekersjoel237.koracore.domain.vo.Amount;
import com.geekersjoel237.koracore.domain.vo.Id;

import java.time.Duration;
import java.time.Instant;

public class AuthorizationRecord {

    private final Id id;
    private final Id transactionId;
    private final String providerReference;
    private final Amount authorizedAmount;
    private final Instant authorizedAt;
    private final Instant expiresAt;
    private AuthorizationStatus status;

    public enum AuthorizationStatus {
        ACTIVE, EXPIRED, CONSUMED, CANCELLED
    }

    private AuthorizationRecord(Id id, Id transactionId, String providerReference,
                                Amount authorizedAmount, Instant authorizedAt,
                                Instant expiresAt, AuthorizationStatus status) {
        this.id = id;
        this.transactionId = transactionId;
        this.providerReference = providerReference;
        this.authorizedAmount = authorizedAmount;
        this.authorizedAt = authorizedAt;
        this.expiresAt = expiresAt;
        this.status = status;
    }

    public static AuthorizationRecord create(Id transactionId,
                                             String providerReference,
                                             Amount amount,
                                             Duration ttl) {
        Instant now = Instant.now();
        return new AuthorizationRecord(
                Id.generate(), transactionId, providerReference,
                amount, now, now.plus(ttl), AuthorizationStatus.ACTIVE);
    }

    public static AuthorizationRecord createFromSnapshot(Snapshot snap) {
        return new AuthorizationRecord(
                snap.id(), snap.transactionId(), snap.providerReference(),
                snap.authorizedAmount(), snap.authorizedAt(),
                snap.expiresAt(), snap.status());
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isActive() {
        return status == AuthorizationStatus.ACTIVE && !isExpired();
    }

    public void consume() {
        this.status = AuthorizationStatus.CONSUMED;
    }

    public void cancel() {
        this.status = AuthorizationStatus.CANCELLED;
    }

    public void expire() {
        this.status = AuthorizationStatus.EXPIRED;
    }

    public Id transactionId() {
        return transactionId;
    }

    public Snapshot snapshot() {
        return new Snapshot(id, transactionId, providerReference,
                authorizedAmount, authorizedAt, expiresAt, status);
    }

    public record Snapshot(
            Id id,
            Id transactionId,
            String providerReference,
            Amount authorizedAmount,
            Instant authorizedAt,
            Instant expiresAt,
            AuthorizationStatus status
    ) {}
}