package com.geekersjoel237.koracore.payment.unit.domain.authorization;

import com.geekersjoel237.koracore.payment.domain.model.AuthorizationRecord;
import com.geekersjoel237.koracore.payment.domain.model.AuthorizationRecord.AuthorizationStatus;
import com.geekersjoel237.koracore.shared.domain.vo.Amount;
import com.geekersjoel237.koracore.shared.domain.vo.Id;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class AuthorizationRecordTest {

    private static final Id TX_ID = new Id("tx-001");
    private static final String PROVIDER_REF = "provider-ref-abc";
    private static final Amount AMOUNT = Amount.of(BigDecimal.valueOf(10_000), "XAF");
    private static final Duration TTL_15_MIN = Duration.ofMinutes(15);

    private AuthorizationRecord freshRecord() {
        return AuthorizationRecord.create(TX_ID, PROVIDER_REF, AMOUNT, TTL_15_MIN);
    }

    @Test
    void create_should_set_status_to_active() {
        AuthorizationRecord record = freshRecord();
        assertEquals(AuthorizationStatus.ACTIVE, record.snapshot().status());
    }

    @Test
    void create_should_set_expires_at_as_authorized_at_plus_ttl() {
        Instant before = Instant.now();
        AuthorizationRecord record = freshRecord();
        Instant after = Instant.now();

        Instant authorizedAt = record.snapshot().authorizedAt();
        Instant expiresAt = record.snapshot().expiresAt();

        assertTrue(authorizedAt.compareTo(before) >= 0 && authorizedAt.compareTo(after) <= 0,
                "authorizedAt should be within test window");
        assertEquals(authorizedAt.plus(TTL_15_MIN), expiresAt);
    }

    @Test
    void isExpired_should_return_false_when_within_ttl() {
        AuthorizationRecord record = freshRecord();
        assertFalse(record.isExpired());
    }

    @Test
    void isExpired_should_return_true_after_ttl_has_passed() {
        // Create a record whose expiresAt is in the past
        AuthorizationRecord record = AuthorizationRecord.createFromSnapshot(
                new AuthorizationRecord.Snapshot(
                        Id.generate(), TX_ID, PROVIDER_REF, AMOUNT,
                        Instant.now().minus(Duration.ofMinutes(16)),
                        Instant.now().minus(Duration.ofMinutes(1)),  // already expired
                        AuthorizationStatus.ACTIVE
                )
        );
        assertTrue(record.isExpired());
    }

    @Test
    void isActive_should_return_false_when_expired_but_status_still_active() {
        AuthorizationRecord record = AuthorizationRecord.createFromSnapshot(
                new AuthorizationRecord.Snapshot(
                        Id.generate(), TX_ID, PROVIDER_REF, AMOUNT,
                        Instant.now().minus(Duration.ofMinutes(16)),
                        Instant.now().minus(Duration.ofMinutes(1)),
                        AuthorizationStatus.ACTIVE
                )
        );
        assertFalse(record.isActive());
    }

    @Test
    void isActive_should_return_false_when_status_is_consumed() {
        AuthorizationRecord record = freshRecord();
        record.consume();
        assertFalse(record.isActive());
    }

    @Test
    void isActive_should_return_false_when_status_is_cancelled() {
        AuthorizationRecord record = freshRecord();
        record.cancel();
        assertFalse(record.isActive());
    }

    @Test
    void consume_should_set_status_to_consumed_and_deactivate() {
        AuthorizationRecord record = freshRecord();
        record.consume();
        assertEquals(AuthorizationStatus.CONSUMED, record.snapshot().status());
        assertFalse(record.isActive());
    }

    @Test
    void cancel_should_set_status_to_cancelled_and_deactivate() {
        AuthorizationRecord record = freshRecord();
        record.cancel();
        assertEquals(AuthorizationStatus.CANCELLED, record.snapshot().status());
        assertFalse(record.isActive());
    }

    @Test
    void expire_should_set_status_to_expired_and_deactivate() {
        AuthorizationRecord record = freshRecord();
        record.expire();
        assertEquals(AuthorizationStatus.EXPIRED, record.snapshot().status());
        assertFalse(record.isActive());
    }

    @Test
    void snapshot_should_return_all_fields() {
        AuthorizationRecord record = freshRecord();
        AuthorizationRecord.Snapshot snap = record.snapshot();

        assertNotNull(snap.id());
        assertEquals(TX_ID, snap.transactionId());
        assertEquals(PROVIDER_REF, snap.providerReference());
        assertEquals(AMOUNT, snap.authorizedAmount());
        assertNotNull(snap.authorizedAt());
        assertNotNull(snap.expiresAt());
        assertEquals(AuthorizationStatus.ACTIVE, snap.status());
    }
}