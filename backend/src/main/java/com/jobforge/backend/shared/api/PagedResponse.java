package com.jobforge.backend.shared.api;

import java.util.List;

/**
 * Controller return type for offset-paginated lists. The envelope advice converts it to
 * {@code { data: [...], meta: { page: {...} } }}.
 */
public record PagedResponse<T>(List<T> items, PageMeta page) {}
