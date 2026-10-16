package com.geekersjoel237.koracore.auth.adapters.in.rest.api.shared;

import com.geekersjoel237.koracore.auth.domain.vo.Tokens;

import java.time.Instant;

public record TokensResponse(
        String accessToken,
        Instant accessTokenExpiry,
        String refreshToken,
        Instant refreshTokenExpiry
) {
    public static TokensResponse from(Tokens tokens) {
        return new TokensResponse(
                tokens.accessToken().token().value(),
                tokens.accessToken().expiresAt(),
                tokens.refreshToken().token().value(),
                tokens.refreshToken().expiresAt()
        );
    }
}