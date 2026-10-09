package com.jobforge.backend.shared.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

/** {@code meta} object of the success envelope (API_CONTRACT §4). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ResponseMeta(String requestId, Instant timestamp, PageMeta page, CursorMeta cursor) {

    public static ResponseMeta of(String requestId, Instant timestamp) {
        return new ResponseMeta(requestId, timestamp, null, null);
    }
}
