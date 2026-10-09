package com.jobforge.backend.shared.error;

import java.util.List;

/** 400 VALIDATION_FAILED raised by service-level validation (field details use API_CONTRACT §5.1 codes). */
public class ValidationFailedException extends ApiException {

    public ValidationFailedException(List<FieldErrorDetail> details) {
        super(ErrorCode.VALIDATION_FAILED, "One or more fields are invalid.", details);
    }

    public static ValidationFailedException of(String field, String code, String message) {
        return new ValidationFailedException(List.of(new FieldErrorDetail(field, code, message)));
    }
}
