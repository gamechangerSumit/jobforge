package com.jobforge.backend.community.domain;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Tag normalisation: lower-case slug {@code [a-z0-9-]{2,30}} (DATABASE_SCHEMA 5, API_CONTRACT 10). */
public final class Tags {
    private static final Pattern SLUG = Pattern.compile("^[a-z0-9-]{2,30}$");

    private Tags() {}

    public static boolean isValidSlug(String slug) {
        return slug != null && SLUG.matcher(slug).matches();
    }

    /** Lower-cases and trims; returns null when the result is not a valid slug. */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String slug = raw.strip().toLowerCase(Locale.ROOT);
        return isValidSlug(slug) ? slug : null;
    }

    /** Distinct normalised slugs in input order; invalid entries are reported through {@code invalid}. */
    public static Set<String> normalizeAll(List<String> raw, List<String> invalid) {
        Set<String> out = new LinkedHashSet<>();
        if (raw == null) {
            return out;
        }
        for (String value : raw) {
            String slug = normalize(value);
            if (slug == null) {
                invalid.add(value == null ? "null" : value);
            } else {
                out.add(slug);
            }
        }
        return out;
    }
}
