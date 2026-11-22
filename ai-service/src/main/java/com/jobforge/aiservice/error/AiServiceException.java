package com.jobforge.aiservice.error;

import java.time.Instant;
import java.util.List;

/** Safe typed exception; provider and database internals are never exposed to callers. */
public class AiServiceException extends RuntimeException {
    public record Detail(String field, String code, String message) {}
    private final AiErrorCode code;
    private final transient List<Detail> details;
    private final Long retryAfterSeconds;

    public AiServiceException(AiErrorCode code, String message) { this(code, message, List.of(), null); }
    public AiServiceException(AiErrorCode code, String message, List<Detail> details, Long retryAfterSeconds) {
        super(message);
        this.code = code;
        this.details = details == null ? List.of() : List.copyOf(details);
        this.retryAfterSeconds = retryAfterSeconds;
    }
    public AiErrorCode code() { return code; }
    public List<Detail> details() { return details; }
    public Long retryAfterSeconds() { return retryAfterSeconds; }
    public static AiServiceException validation(String field, String code, String message) {
        return new AiServiceException(AiErrorCode.VALIDATION_FAILED, "One or more fields are invalid.",
                List.of(new Detail(field, code, message)), null);
    }
    public static AiServiceException notFound() {
        return new AiServiceException(AiErrorCode.RESOURCE_NOT_FOUND, "The requested resource was not found.");
    }
    public static AiServiceException quotaExceeded(Instant resetsAt, long retryAfterSeconds) {
        return new AiServiceException(AiErrorCode.AI_QUOTA_EXCEEDED, "Your AI usage quota is exhausted for this period.",
                List.of(new Detail("resetsAt", "QUOTA_RESET", resetsAt.toString())), Math.max(1, retryAfterSeconds));
    }
    public static AiServiceException rateLimited(long seconds) {
        return new AiServiceException(AiErrorCode.RATE_LIMITED, "Too many AI requests. Please retry later.", List.of(), Math.max(1, seconds));
    }
    public static AiServiceException providerUnavailable() {
        return new AiServiceException(AiErrorCode.AI_PROVIDER_UNAVAILABLE, "The AI provider is currently unavailable.");
    }
    public static AiServiceException timeout() {
        return new AiServiceException(AiErrorCode.AI_TIMEOUT, "The AI provider did not answer in time.");
    }
    public static AiServiceException outputInvalid() {
        return new AiServiceException(AiErrorCode.AI_OUTPUT_INVALID, "The AI output did not pass validation.");
    }
}
