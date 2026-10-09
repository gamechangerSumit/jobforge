package com.jobforge.backend.shared.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Guards the catalog against drift from API_CONTRACT §5.1. */
class ErrorCodeTest {

    private static final Map<String, Integer> CONTRACT = Map.ofEntries(
            Map.entry("VALIDATION_FAILED", 400), Map.entry("MALFORMED_REQUEST", 400),
            Map.entry("AUTH_UNAUTHENTICATED", 401), Map.entry("AUTH_TOKEN_EXPIRED", 401),
            Map.entry("AUTH_INVALID_CREDENTIALS", 401), Map.entry("AUTH_REFRESH_INVALID", 401),
            Map.entry("AUTH_REFRESH_REUSED", 401), Map.entry("ACCESS_DENIED", 403),
            Map.entry("EMAIL_NOT_VERIFIED", 403), Map.entry("ACCOUNT_SUSPENDED", 403),
            Map.entry("RECRUITER_NOT_APPROVED", 403), Map.entry("COMPANY_NOT_VERIFIED", 403),
            Map.entry("RESOURCE_NOT_FOUND", 404), Map.entry("CONFLICT", 409),
            Map.entry("EMAIL_ALREADY_REGISTERED", 409), Map.entry("HANDLE_TAKEN", 409),
            Map.entry("DUPLICATE_APPLICATION", 409), Map.entry("INVALID_STATE_TRANSITION", 409),
            Map.entry("STALE_VERSION", 409), Map.entry("PAYLOAD_TOO_LARGE", 413),
            Map.entry("UNSUPPORTED_MEDIA_TYPE", 415), Map.entry("BUSINESS_RULE_VIOLATED", 422),
            Map.entry("RATE_LIMITED", 429), Map.entry("AI_QUOTA_EXCEEDED", 429),
            Map.entry("INTERNAL_ERROR", 500), Map.entry("AI_OUTPUT_INVALID", 502),
            Map.entry("AI_PROVIDER_UNAVAILABLE", 503), Map.entry("AI_TIMEOUT", 504));

    @Test
    void catalogMatchesContractExactly() {
        Map<String, Integer> actual = Arrays.stream(ErrorCode.values())
                .collect(Collectors.toMap(Enum::name, c -> c.status().value()));
        assertThat(actual).isEqualTo(CONTRACT);
    }

    @Test
    void conflictExceptionRejectsNon409Codes() {
        assertThatThrownBy(() -> new ConflictException(ErrorCode.ACCESS_DENIED, "x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new ConflictException(ErrorCode.STALE_VERSION, "x").errorCode()).isEqualTo(ErrorCode.STALE_VERSION);
    }

    @Test
    void forbiddenExceptionRejectsNon403Codes() {
        assertThatThrownBy(() -> new ForbiddenException(ErrorCode.CONFLICT, "x"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rateLimitedExceptionHasAtLeastOneSecondRetryAfter() {
        assertThat(new RateLimitedException("slow down", 0).retryAfterSeconds()).isEqualTo(1);
    }
}
