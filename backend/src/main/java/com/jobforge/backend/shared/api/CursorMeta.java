package com.jobforge.backend.shared.api;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Cursor pagination meta (API_CONTRACT §4, §6). {@code next} is serialized as null when absent. */
public record CursorMeta(@JsonInclude(JsonInclude.Include.ALWAYS) String next, boolean hasNext, int limit) {}
