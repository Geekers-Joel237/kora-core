package com.geekersjoel237.koracore.auth.domain.vo;

import java.security.SecureRandom;
import java.util.regex.Pattern;

/**
 * A six-digit one-time code, issued or submitted.
 *
 * <p>One type for both directions on purpose. There used to be a second, {@code Otp},
 * that added a TTL and a {@code createdAt} — a code that knew when it died. Now the
 * store owns the lifetime, so what was left of it was a code and nothing else.
 */
public record OtpCode(String value) {

    private static final Pattern SIX_DIGITS = Pattern.compile("\\d{6}");
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public OtpCode {
        if (value == null || !SIX_DIGITS.matcher(value).matches())
            throw new IllegalArgumentException("OTP code must be exactly 6 digits");
    }

    public static OtpCode of(String value) {
        return new OtpCode(value);
    }

    /** Padded, so a draw below 100000 is still six digits and still a legal code. */
    public static OtpCode generate() {
        return new OtpCode(String.format("%06d", SECURE_RANDOM.nextInt(1_000_000)));
    }

    /** Never the value: this lands in logs and in exception messages. */
    @Override
    public String toString() {
        return "***";
    }
}
