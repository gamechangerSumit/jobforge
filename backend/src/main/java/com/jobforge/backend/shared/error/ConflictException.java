package com.jobforge.backend.shared.error;

/** 409 family: CONFLICT, EMAIL_ALREADY_REGISTERED, HANDLE_TAKEN, DUPLICATE_APPLICATION, INVALID_STATE_TRANSITION, STALE_VERSION. */
public class ConflictException extends ApiException {

    public ConflictException(String message) {
        this(ErrorCode.CONFLICT, message);
    }

    public ConflictException(ErrorCode errorCode, String message) {
        super(requireStatus(errorCode, 409), message);
    }

    static ErrorCode requireStatus(ErrorCode code, int expected) {
        if (code.status().value() != expected) {
            throw new IllegalArgumentException(code + " is not a " + expected + " error code");
        }
        return code;
    }
}
