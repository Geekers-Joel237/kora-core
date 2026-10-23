package com.geekersjoel237.koracore.shared.application.cqrs.paging;

public record Pagination(int limit) {

    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 100;

    public Pagination {
        if (limit < 1)
            throw new IllegalArgumentException("Limit must be at least 1, got: " + limit);
        if (limit > MAX_LIMIT)
            throw new IllegalArgumentException("Limit cannot exceed " + MAX_LIMIT + ", got: " + limit);
    }

    public static Pagination firstPage() {
        return new Pagination(DEFAULT_LIMIT);
    }

    public static Pagination of(Integer limit) {
        return new Pagination(limit == null ? DEFAULT_LIMIT : limit);
    }

    // One row past the limit tells whether a next page exists, without counting the set.
    public int fetchSize() {
        return limit + 1;
    }
}
