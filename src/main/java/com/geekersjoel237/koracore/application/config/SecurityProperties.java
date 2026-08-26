package com.geekersjoel237.koracore.application.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Security configuration, validated at startup.
 * <p>
 * {@code ${JWT_SECRET}} in {@code application.properties} carries no fallback, so an
 * absent variable already stops the boot. These constraints catch the other half:
 * a variable that is present but unusable — blank, or too short for HMAC-SHA256 —
 * and name the offending property and the rule instead of failing later inside jjwt.
 */
@Validated
@ConfigurationProperties(prefix = "kora.security")
public record SecurityProperties(@Valid Jwt jwt, @Valid Otp otp) {

    /** Shortest key HMAC-SHA256 accepts: 256 bits. */
    private static final int MIN_SECRET_LENGTH = 32;

    public record Jwt(
            @NotBlank(message = "must be set — generate one with: openssl rand -base64 48")
            @Size(min = MIN_SECRET_LENGTH,
                  message = "must be at least {min} characters for HMAC-SHA256")
            String secret,

            @Positive int accessTokenExpirationMinutes,
            @Positive int refreshTokenExpirationDays
    ) {}

    public record Otp(
            @Positive int expirationMinutes
    ) {}
}
