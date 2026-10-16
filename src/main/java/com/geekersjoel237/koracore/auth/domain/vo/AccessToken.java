package com.geekersjoel237.koracore.auth.domain.vo;


public record AccessToken(String value) {

    public AccessToken {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("AccessToken cannot be blank");
    }

    public static AccessToken of(String value) {
        return new AccessToken(value);
    }

    @Override
    public String toString() {
        return "***";
    }
}
