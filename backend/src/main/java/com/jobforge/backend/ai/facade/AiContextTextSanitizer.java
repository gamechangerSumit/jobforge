package com.jobforge.backend.ai.facade;

import java.util.regex.Pattern;

/** Redacts common contact details from free-text context before it is sent to an AI provider. */
public final class AiContextTextSanitizer {
    private static final Pattern EMAIL = Pattern.compile("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b");
    private static final Pattern URL = Pattern.compile("(?i)\\b(?:https?://|www\\.)\\S+");
    private static final Pattern PHONE = Pattern.compile("(?<!\\w)\\+?\\d[\\d\\s().-]{7,}\\d(?!\\w)");

    private AiContextTextSanitizer() {}

    public static String sanitize(String value) {
        if (value == null || value.isBlank()) return value;
        String safe = EMAIL.matcher(value).replaceAll("[redacted]");
        safe = URL.matcher(safe).replaceAll("[redacted]");
        safe = PHONE.matcher(safe).replaceAll("[redacted]");
        safe = safe.replaceAll("\\s{2,}", " ").trim();
        return safe.length() > 1000 ? safe.substring(0, 1000) : safe;
    }
}
