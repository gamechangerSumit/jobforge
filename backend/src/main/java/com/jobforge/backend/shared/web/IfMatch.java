package com.jobforge.backend.shared.web;

import com.jobforge.backend.shared.error.ApiException;
import com.jobforge.backend.shared.error.ErrorCode;

/** Parses the optional {@code If-Match: "<version>"} header (API_CONTRACT §9). */
public final class IfMatch {

    private IfMatch() {}

    /** @return the version, or null when the header is absent. */
    public static Long parse(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        String value = header.trim();
        if (value.startsWith("W/")) {
            value = value.substring(2);
        }
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new ApiException(ErrorCode.MALFORMED_REQUEST, "The If-Match header must contain a version number.");
        }
    }
}
