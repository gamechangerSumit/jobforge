package com.jobforge.aiservice.domain;

import java.util.Set;

/** AI operation costs and caller-role policy. */
public enum AiOperation {
    JOB_GENERATION(5, true, Set.of("RECRUITER")),
    JOB_VALIDATION(1, false, Set.of("RECRUITER")),
    JOB_RECOMMENDATION(1, false, Set.of("JOB_SEEKER")),
    CANDIDATE_RECOMMENDATION(2, false, Set.of("RECRUITER")),
    CAREER_INSIGHT(2, false, Set.of("JOB_SEEKER"));

    private final int credits;
    private final boolean idempotencyKeyRequired;
    private final Set<String> allowedRoles;

    AiOperation(int credits, boolean idempotencyKeyRequired, Set<String> allowedRoles) {
        this.credits = credits;
        this.idempotencyKeyRequired = idempotencyKeyRequired;
        this.allowedRoles = allowedRoles;
    }
    public int credits() { return credits; }
    public boolean idempotencyKeyRequired() { return idempotencyKeyRequired; }
    public boolean allows(String role) { return allowedRoles.contains(role); }
}
