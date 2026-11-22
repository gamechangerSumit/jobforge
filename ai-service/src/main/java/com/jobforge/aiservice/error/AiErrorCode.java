package com.jobforge.aiservice.error;

import org.springframework.http.HttpStatus;

/** Public error catalog emitted by ai-service. */
public enum AiErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST),
    AUTH_UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),
    ACCESS_DENIED(HttpStatus.FORBIDDEN),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),
    CONFLICT(HttpStatus.CONFLICT),
    PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE),
    BUSINESS_RULE_VIOLATED(HttpStatus.UNPROCESSABLE_ENTITY),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),
    AI_QUOTA_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),
    AI_OUTPUT_INVALID(HttpStatus.BAD_GATEWAY),
    AI_PROVIDER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
    AI_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT);
    private final HttpStatus status;
    AiErrorCode(HttpStatus status) { this.status = status; }
    public HttpStatus status() { return status; }
}
