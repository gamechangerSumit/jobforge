package com.jobforge.backend.shared.api;

import com.jobforge.backend.shared.error.ValidationFailedException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Whitelisted {@code sort=<field>,<asc|desc>} → ORDER BY (API_CONTRACT §8). Only whitelist values reach SQL. */
public final class SortSpec {

    private SortSpec() {}

    /**
     * @param sorts        raw {@code sort} parameter values (max 2)
     * @param allowed      API field name → SQL expression
     * @param defaultOrder ORDER BY body used when none is requested
     * @param tieBreaker   always appended (deterministic ordering)
     */
    public static String orderBy(List<String> sorts, Map<String, String> allowed, String defaultOrder, String tieBreaker) {
        if (sorts == null || sorts.isEmpty()) {
            return " ORDER BY " + defaultOrder + ", " + tieBreaker;
        }
        if (sorts.size() > 2) {
            throw ValidationFailedException.of("sort", "SIZE", "at most 2 sort fields are allowed");
        }
        List<String> parts = new ArrayList<>();
        for (String raw : sorts) {
            String[] pair = raw.split(",", 2);
            String column = allowed.get(pair[0].trim());
            if (column == null) {
                throw ValidationFailedException.of("sort", "INVALID_ENUM", "unsupported sort field");
            }
            String direction = pair.length > 1 ? pair[1].trim().toLowerCase(Locale.ROOT) : "asc";
            if (!direction.equals("asc") && !direction.equals("desc")) {
                throw ValidationFailedException.of("sort", "INVALID_ENUM", "direction must be asc or desc");
            }
            parts.add(column + " " + direction.toUpperCase(Locale.ROOT) + " NULLS LAST");
        }
        return " ORDER BY " + String.join(", ", parts) + ", " + tieBreaker;
    }
}
