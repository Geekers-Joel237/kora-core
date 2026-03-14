package com.geekersjoel237.koracore.domain.query;

/**
 * Domain-owned pagination request. Kept separate from Spring's PageRequest
 * so the domain port has no Spring dependency.
 */
public record PageRequest(int page, int size) {

    public PageRequest {
        if (page < 0)
            throw new IllegalArgumentException("Page must be >= 0, got: " + page);
        if (size < 1 || size > 100)
            throw new IllegalArgumentException("Size must be between 1 and 100, got: " + size);
    }
}