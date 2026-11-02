package com.geekersjoel237.koracore.auth.domain.vo;

import java.time.Instant;

public record Tokens(Issued<AccessToken> accessToken, Issued<RefreshToken> refreshToken) {

    public record Issued<T>(T token, Instant expiresAt) {
    }
}
