package com.jobforge.backend.shared.error;

/**
 * 403 family: ACCESS_DENIED, EMAIL_NOT_VERIFIED, ACCOUNT_SUSPENDED,
 * RECRUITER_NOT_APPROVED, COMPANY_NOT_VERIFIED. Spring Security's AccessDeniedException
 * is mapped in Phase 1 when Spring Security is introduced.
 */
public class ForbiddenException extends ApiException {

    public ForbiddenException(String message) {
        this(ErrorCode.ACCESS_DENIED, message);
    }

    public ForbiddenException(ErrorCode errorCode, String message) {
        super(ConflictException.requireStatus(errorCode, 403), message);
    }
}
