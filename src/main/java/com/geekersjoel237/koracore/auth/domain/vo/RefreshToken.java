package com.geekersjoel237.koracore.auth.domain.vo;


public record RefreshToken(String value) {

    public RefreshToken {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("RefreshToken cannot be blank");
    }

    public static RefreshToken of(String value) {
        return new RefreshToken(value);
    }

    @Override
    public String toString() {
        return "***";
    }
}
