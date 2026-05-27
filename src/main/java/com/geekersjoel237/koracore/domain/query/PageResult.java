package com.geekersjoel237.koracore.domain.query;

import java.util.List;

/**
 * Domain-owned paginated result. No Spring dependency.
 */
public record PageResult<T>(List<T> content, int page, int size, long totalElements) {

    public int totalPages() {
        return size > 0 ? (int) Math.ceil((double) totalElements / size) : 0;
    }

    public boolean hasNext() {
        return page < totalPages() - 1;
    }
}