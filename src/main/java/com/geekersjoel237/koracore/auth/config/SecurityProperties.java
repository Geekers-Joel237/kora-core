package com.geekersjoel237.koracore.auth.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;



@Validated
@ConfigurationProperties(prefix = "kora.security")
public record SecurityProperties(@Valid Jwt jwt, @Valid Otp otp) {

    /** Shortest key HMAC-SHA256 accepts: 256 bits. */
    private static final int MIN_SECRET_LENGTH = 32;

    // One key per token use: a token can then only be genuine for the use it was minted for,
    // whatever its claims say (L-10).
    public record Jwt(
            @NotBlank(message = "must be set — generate one with: openssl rand -base64 48")
            @Size(min = MIN_SECRET_LENGTH,
                  message = "must be at least {min} characters for HMAC-SHA256")
            String accessSecret,

            @NotBlank(message = "must be set — generate one with: openssl rand -base64 48")
            @Size(min = MIN_SECRET_LENGTH,
                  message = "must be at least {min} characters for HMAC-SHA256")
            String refreshSecret,

            @Positive int accessTokenExpirationMinutes,
            @Positive int refreshTokenExpirationDays
    ) {
        @AssertTrue(message = "access-secret and refresh-secret must differ")
        public boolean isDistinctSecrets() {
            return accessSecret == null || !accessSecret.equals(refreshSecret);
        }
    }

    public record Otp(
            @Positive int expirationMinutes
    ) {}
}
