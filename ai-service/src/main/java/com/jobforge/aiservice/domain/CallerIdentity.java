package com.jobforge.aiservice.domain;

import java.util.Set;
import java.util.UUID;

/** Identity forwarded by the trusted backend; never a client-supplied JWT claim. */
public record CallerIdentity(UUID userId, String role) {
    public static final Set<String> ROLES = Set.of("JOB_SEEKER", "RECRUITER", "ADMIN");
    public CallerIdentity {
        if (userId == null || role == null || !ROLES.contains(role)) {
            throw new IllegalArgumentException("Invalid caller identity");
        }
    }
}
