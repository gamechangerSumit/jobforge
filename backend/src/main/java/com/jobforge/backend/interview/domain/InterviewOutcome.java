package com.jobforge.backend.interview.domain;

/** Request value of {@code POST /interviews/{id}/complete} ({@code COMPLETED|NO_SHOW}, API_CONTRACT §12.7). */
public enum InterviewOutcome {
    COMPLETED, NO_SHOW
}
