package com.geekersjoel237.koracore.domain.vo;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class OtpTest {

    private static final String VALID_CODE = "123456";
    private static final Duration TTL = Duration.ofMinutes(5);

    // ── Construction valide ───────────────────────────────────────────────────

    @Test
    void should_create_valid_otp_with_correct_fields() {
        Clock clock = Clock.systemUTC();
        Otp otp = Otp.of(VALID_CODE, TTL, clock);

        assertEquals(VALID_CODE, otp.code());
        assertEquals(TTL, otp.ttl());
        assertNotNull(otp.createdAt());
    }

    // ── Validation code ───────────────────────────────────────────────────────

    @Test
    void should_throw_when_code_is_null() {
        assertThrows(IllegalArgumentException.class,
                () -> new Otp(null, TTL, Instant.now()));
    }

    @Test
    void should_throw_when_code_is_blank() {
        assertThrows(IllegalArgumentException.class,
                () -> new Otp("   ", TTL, Instant.now()));
    }


    // ── Validation ttl ────────────────────────────────────────────────────────

    @Test
    void should_throw_when_ttl_is_null() {
        assertThrows(IllegalArgumentException.class,
                () -> new Otp(VALID_CODE, null, Instant.now()));
    }

    // ── Validation createdAt ──────────────────────────────────────────────────

    @Test
    void should_throw_when_created_at_is_null() {
        assertThrows(IllegalArgumentException.class,
                () -> new Otp(VALID_CODE, TTL, null));
    }

    // ── isExpired ─────────────────────────────────────────────────────────────

    @Test
    void should_not_be_expired_before_ttl_elapses() {
        Otp otp = Otp.of(VALID_CODE, TTL, Clock.systemUTC());
        assertFalse(otp.isExpired(Clock.systemUTC()));
    }

    @Test
    void should_be_expired_after_ttl_elapses() {
        Otp otp = Otp.of(VALID_CODE, TTL, Clock.systemUTC());
        // check clock is fixed 1 second after expiry
        Clock futureClock = Clock.fixed(
                otp.createdAt().plus(TTL).plusSeconds(1), ZoneOffset.UTC);
        assertTrue(otp.isExpired(futureClock));
    }

    // ── matches ───────────────────────────────────────────────────────────────

    @Test
    void should_match_correct_code() {
        Otp otp = Otp.of(VALID_CODE, TTL, Clock.systemUTC());
        assertTrue(otp.matches(VALID_CODE));
    }

    @Test
    void should_not_match_wrong_code() {
        Otp otp = Otp.of(VALID_CODE, TTL, Clock.systemUTC());
        assertFalse(otp.matches("000000"));
    }

    // ── equals (record) ───────────────────────────────────────────────────────

    @Test
    void should_equal_two_otps_with_same_fields() {
        Instant now = Instant.now();
        Otp otp1 = new Otp(VALID_CODE, TTL, now);
        Otp otp2 = new Otp(VALID_CODE, TTL, now);
        assertEquals(otp1, otp2);
    }
}