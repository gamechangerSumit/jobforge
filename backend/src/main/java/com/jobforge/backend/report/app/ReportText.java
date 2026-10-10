package com.jobforge.backend.report.app;

import com.jobforge.backend.shared.error.ValidationFailedException;

/**
 * Text normalization shared by report filing ({@code details}) and moderation ({@code reason}).
 *
 * <p>Trimming uses exactly the characters ECMAScript {@code String.prototype.trim()} removes, so the frontend and the
 * backend agree on what is blank and what the trimmed value is. {@code String.strip()/isBlank()} are not used: they
 * keep NBSP (U+00A0, U+202F) and BOM (U+FEFF) padding.
 */
public final class ReportText {

    /** Maximum report {@code details} length in UTF-16 code units ({@code String.length()}), measured BEFORE trimming. */
    public static final int DETAILS_MAX = 1000;

    private ReportText() {}

    /** Removes leading/trailing ECMAScript whitespace and line terminators. */
    public static String trim(String raw) {
        int start = 0;
        int end = raw.length();
        while (start < end && isTrimmable(raw.charAt(start))) {
            start++;
        }
        while (end > start && isTrimmable(raw.charAt(end - 1))) {
            end--;
        }
        return raw.substring(start, end);
    }

    /**
     * Report {@code details}: the raw value must be at most {@link #DETAILS_MAX} UTF-16 code units (so padding counts;
     * a supplementary character such as an emoji counts as 2), then it is trimmed; a value that is empty after
     * trimming (or {@code null}) is omitted ({@code null}).
     */
    public static String normalizeDetails(String raw) {
        if (raw == null) {
            return null;
        }
        if (raw.length() > DETAILS_MAX) {
            throw ValidationFailedException.of("details", "SIZE", "must be at most " + DETAILS_MAX + " characters");
        }
        String trimmed = trim(raw);
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** ECMAScript WhiteSpace + LineTerminator: TAB..CR, space, NBSP, U+1680, U+2000-200A, U+2028/2029, U+202F, U+205F, U+3000, U+FEFF. */
    static boolean isTrimmable(char c) {
        return (c >= 0x09 && c <= 0x0D) || c == 0x20 || c == 0xA0 || c == 0x1680 || (c >= 0x2000 && c <= 0x200A)
                || c == 0x2028 || c == 0x2029 || c == 0x202F || c == 0x205F || c == 0x3000 || c == 0xFEFF;
    }
}
