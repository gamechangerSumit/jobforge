package com.jobforge.backend.application.domain;

/** DATABASE_SCHEMA §3.6 {@code application_status}. */
public enum ApplicationStatus {
    SUBMITTED, UNDER_REVIEW, SHORTLISTED, INTERVIEW, OFFERED, HIRED, REJECTED, WITHDRAWN;

    public boolean isTerminal() {
        return this == HIRED || this == REJECTED || this == WITHDRAWN;
    }
}
