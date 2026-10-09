package com.jobforge.backend.shared.error;

import java.util.List;

/**
 * Typed exception carrying an {@link ErrorCode}. Services throw subclasses of this;
 * {@code GlobalExceptionHandler} turns them into the error envelope.
 * The message MUST be safe to display to end users.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;
    private final transient List<FieldErrorDetail> details;

    public ApiException(ErrorCode errorCode, String message) {
        this(errorCode, message, List.of());
    }

    public ApiException(ErrorCode errorCode, String message, List<FieldErrorDetail> details) {
        super(message);
        this.errorCode = errorCode;
        this.details = details == null ? List.of() : List.copyOf(details);
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public List<FieldErrorDetail> details() {
        return details;
    }
}
