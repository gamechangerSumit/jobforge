package com.jobforge.backend.shared.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.jobforge.backend.shared.error.FieldErrorDetail;
import java.time.Instant;
import java.util.List;

/** The {@code error} object (API_CONTRACT §5). {@code details} is omitted unless present. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorBody(
        String code,
        String message,
        int status,
        String requestId,
        Instant timestamp,
        String path,
        List<FieldErrorDetail> details) {}
