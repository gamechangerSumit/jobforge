package com.jobforge.backend.shared.api;

/** Offset pagination meta (API_CONTRACT §4, §6). {@code number} is 0-based. */
public record PageMeta(int number, int size, long totalElements, int totalPages, boolean hasNext) {

    public static PageMeta of(int number, int size, long totalElements) {
        int totalPages = size <= 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        return new PageMeta(number, size, totalElements, totalPages, number + 1 < totalPages);
    }
}
