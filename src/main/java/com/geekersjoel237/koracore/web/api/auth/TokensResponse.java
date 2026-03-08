package com.geekersjoel237.koracore.web.api.auth;

import com.geekersjoel237.koracore.domain.vo.Tokens;

import java.time.Instant;

public record TokensResponse(
        String accessToken,
        Instant accessTokenExpiry,
        String refreshToken,
        Instant refreshTokenExpiry
) {
    public static TokensResponse from(Tokens tokens) {
        return new TokensResponse(
                tokens.accessToken().value(),
                tokens.accessToken().expiredAt(),
                tokens.refreshToken().value(),
                tokens.refreshToken().expiredAt()
        );
    }
}