package com.geekersjoel237.koracore.shared.application.cqrs.paging;

import java.util.List;
import java.util.function.Function;


public record PageResult<T>(List<T> content, int page, int size, long totalElements) {

    public static <T> PageResult<T> of(List<T> content, Pagination pagination, long totalElements) {
        return new PageResult<>(content, pagination.page(), pagination.size(), totalElements);
    }

    public static <T> PageResult<T> empty(Pagination pagination) {
        return new PageResult<>(List.of(), pagination.page(), pagination.size(), 0);
    }


    public <R> PageResult<R> map(Function<? super T, ? extends R> mapper) {
        return new PageResult<>(content.stream().<R>map(mapper).toList(),
                page, size, totalElements);
    }

    public int totalPages() {
        return size > 0 ? (int) Math.ceil((double) totalElements / size) : 0;
    }

    public boolean hasNext() {
        return page < totalPages() - 1;
    }
}
