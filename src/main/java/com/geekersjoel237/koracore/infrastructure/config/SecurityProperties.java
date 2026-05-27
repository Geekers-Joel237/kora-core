package com.geekersjoel237.koracore.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kora.security")
public record SecurityProperties(Jwt jwt, Otp otp) {

    public record Jwt(
            String secret,
            int accessTokenExpirationMinutes,
            int refreshTokenExpirationDays
    ) {}

    public record Otp(
            int expirationMinutes
    ) {}
}