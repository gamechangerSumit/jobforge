package com.jobforge.backend.shared.api;

import java.util.List;
import java.util.function.Function;

/** Repository-level page: items plus the total element count. */
public record PagedResult<T>(List<T> items, long total) {

    public <R> PagedResponse<R> toResponse(Function<T, R> mapper, int page, int size) {
        return new PagedResponse<>(items.stream().map(mapper).toList(), PageMeta.of(page, size, total));
    }

    public static <T> PagedResult<T> empty() {
        return new PagedResult<>(List.of(), 0);
    }
}
