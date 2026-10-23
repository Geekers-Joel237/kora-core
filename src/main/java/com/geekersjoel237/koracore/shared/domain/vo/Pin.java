package com.geekersjoel237.koracore.shared.domain.vo;


public record Pin(String value) {

    public Pin {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("Pin cannot be blank");
    }

    public static Pin of(String value) {
        return new Pin(value);
    }

    @Override
    public String toString() {
        return "***";
    }
}
