package com.geekersjoel237.koracore.shared.application.cqrs.paging;


public record Pagination(int page, int size) {

    public static final int DEFAULT_SIZE = 20;


    public static final int MAX_SIZE = 100;

    public Pagination {
        if (page < 0)
            throw new IllegalArgumentException("Page number cannot be negative, got: " + page);
        if (size < 1)
            throw new IllegalArgumentException("Page size must be at least 1, got: " + size);
        if (size > MAX_SIZE)
            throw new IllegalArgumentException(
                    "Page size cannot exceed " + MAX_SIZE + ", got: " + size);
    }

    public static Pagination firstPage() {
        return new Pagination(0, DEFAULT_SIZE);
    }


    public static Pagination of(Integer page, Integer size) {
        return new Pagination(
                page == null ? 0 : page,
                size == null ? DEFAULT_SIZE : size);
    }

    public long offset() {
        return (long) page * size;
    }
}
