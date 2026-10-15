package com.geekersjoel237.koracore.shared.application.cqrs.paging;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public record PageResult<T>(List<T> content, boolean hasNext) {

    public static <T> PageResult<T> fromFetched(List<T> fetched, Pagination pagination) {
        boolean hasNext = fetched.size() > pagination.limit();
        List<T> content = hasNext ? fetched.subList(0, pagination.limit()) : fetched;
        return new PageResult<>(List.copyOf(content), hasNext);
    }

    public <R> PageResult<R> map(Function<? super T, ? extends R> mapper) {
        return new PageResult<>(content.stream().<R>map(mapper).toList(), hasNext);
    }

    public Optional<T> lastItem() {
        return content.isEmpty() ? Optional.empty() : Optional.of(content.getLast());
    }
}
