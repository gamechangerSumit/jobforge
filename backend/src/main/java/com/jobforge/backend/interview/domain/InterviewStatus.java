package com.jobforge.backend.interview.domain;

/** DATABASE_SCHEMA §3.5 {@code interview_status}. */
public enum InterviewStatus {
    SCHEDULED, CONFIRMED, DECLINED, COMPLETED, CANCELLED, NO_SHOW;

    /** Upcoming interviews that still occupy a time slot. */
    public boolean isActive() {
        return this == SCHEDULED || this == CONFIRMED;
    }

    /** No further change is possible. DECLINED is intentionally not final: the recruiter may reschedule or cancel. */
    public boolean isFinal() {
        return this == COMPLETED || this == CANCELLED || this == NO_SHOW;
    }
}
