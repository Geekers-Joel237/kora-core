package com.geekersjoel237.koracore.domain.vo;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

public record Otp(String code, Duration ttl, Instant createdAt) {

    public Otp {
        if (code == null || code.isBlank())
            throw new IllegalArgumentException("OTP code cannot be blank");
        if (ttl == null)
            throw new IllegalArgumentException("OTP ttl cannot be null");
        if (ttl.isNegative() || ttl.isZero())
            throw new IllegalArgumentException("OTP ttl must be strictly positive");
        if (createdAt == null)
            throw new IllegalArgumentException("OTP createdAt cannot be null");
    }

    public static Otp of(String code, Duration ttl, Clock clock) {
        return new Otp(code, ttl, Instant.now(clock));
    }

    public boolean isExpired(Clock clock) {
        return Instant.now(clock).isAfter(createdAt.plus(ttl));
    }

    public boolean matches(String inputCode) {
        return this.code.equals(inputCode);
    }
}