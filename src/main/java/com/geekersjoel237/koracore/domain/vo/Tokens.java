package com.geekersjoel237.koracore.domain.vo;

import java.time.Instant;

public record Tokens(TokenValue accessToken, TokenValue refreshToken) {
    public record TokenValue(String value, Instant expiredAt) {
    }
}