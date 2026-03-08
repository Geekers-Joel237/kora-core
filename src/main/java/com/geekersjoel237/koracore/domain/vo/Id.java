package com.geekersjoel237.koracore.domain.vo;

import java.util.UUID;

public record Id(String value) {

    public Id {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("Id cannot be blank");
    }

    public static Id generate() {
        return new Id(UUID.randomUUID().toString());
    }
}