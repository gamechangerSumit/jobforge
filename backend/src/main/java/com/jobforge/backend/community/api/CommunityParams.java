package com.jobforge.backend.community.api;

import com.jobforge.backend.community.domain.CommunityLimits;
import com.jobforge.backend.shared.error.ApiException;
import com.jobforge.backend.shared.error.ErrorCode;
import com.jobforge.backend.shared.error.ValidationFailedException;
import com.jobforge.backend.shared.web.QueryParams;

/** Cursor pagination parameters (API_CONTRACT 6): {@code limit} default 20, max 50. */
final class CommunityParams {

    private CommunityParams() {}

    static int limit(QueryParams params) {
        String raw = params.string("limit");
        if (raw == null) {
            return CommunityLimits.CURSOR_DEFAULT_LIMIT;
        }
        int limit;
        try {
            limit = Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new ApiException(ErrorCode.MALFORMED_REQUEST, "Parameter 'limit' must be an integer.");
        }
        if (limit < 1) {
            throw ValidationFailedException.of("limit", "MIN", "must be greater than or equal to 1");
        }
        if (limit > CommunityLimits.CURSOR_MAX_LIMIT) {
            throw ValidationFailedException.of("limit", "MAX", "must be less than or equal to " + CommunityLimits.CURSOR_MAX_LIMIT);
        }
        return limit;
    }
}
