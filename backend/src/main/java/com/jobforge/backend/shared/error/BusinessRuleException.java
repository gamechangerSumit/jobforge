package com.jobforge.backend.shared.error;

import java.util.List;

/** Valid syntax but violates a domain rule. Maps to 422 BUSINESS_RULE_VIOLATED, optionally with {@code details}. */
public class BusinessRuleException extends ApiException {

    public BusinessRuleException(String message) {
        super(ErrorCode.BUSINESS_RULE_VIOLATED, message);
    }

    public BusinessRuleException(String message, List<FieldErrorDetail> details) {
        super(ErrorCode.BUSINESS_RULE_VIOLATED, message, details);
    }
}
