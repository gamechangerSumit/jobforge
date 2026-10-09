package com.jobforge.backend.shared.api;

import java.util.List;

/** Controller return type for cursor-paginated lists. */
public record CursorResponse<T>(List<T> items, CursorMeta cursor) {}
