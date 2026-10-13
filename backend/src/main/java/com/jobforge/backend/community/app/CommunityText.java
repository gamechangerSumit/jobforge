package com.jobforge.backend.community.app;

import com.jobforge.backend.community.domain.CommunityLimits;
import com.jobforge.backend.shared.error.ValidationFailedException;
import com.jobforge.backend.shared.text.MarkdownSanitizer;

/** Server-side normalisation of user text: HTML stripped (API_CONTRACT 10), trimmed, length counted in code points. */
final class CommunityText {

    private CommunityText() {}

    /** Optional title: blank -> null; max 150 code points. */
    static String title(String raw) {
        if (raw == null) {
            return null;
        }
        String value = MarkdownSanitizer.strip(raw).strip();
        if (value.isEmpty()) {
            return null;
        }
        if (value.codePointCount(0, value.length()) > CommunityLimits.TITLE_MAX) {
            throw ValidationFailedException.of("title", "SIZE", "must be at most " + CommunityLimits.TITLE_MAX + " characters");
        }
        return value;
    }

    /** Required body: 1..max code points after HTML stripping and trimming. */
    static String body(String raw, int max, String field) {
        String value = raw == null ? "" : MarkdownSanitizer.strip(raw).strip();
        int length = value.codePointCount(0, value.length());
        if (length < 1 || length > max) {
            throw ValidationFailedException.of(field, "SIZE", "must be between 1 and " + max + " characters");
        }
        return value;
    }
}
