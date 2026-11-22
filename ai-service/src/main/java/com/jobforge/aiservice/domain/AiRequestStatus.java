package com.jobforge.aiservice.domain;

/** Persisted AI request lifecycle states. */
public enum AiRequestStatus {
    PENDING, SUCCEEDED, FAILED, REJECTED_QUOTA, REJECTED_VALIDATION
}
