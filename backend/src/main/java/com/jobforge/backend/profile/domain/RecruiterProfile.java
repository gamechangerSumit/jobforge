package com.jobforge.backend.profile.domain;

import java.time.Instant;
import java.util.UUID;

/** DATABASE_SCHEMA §4.3 recruiter_profiles. {@code approvalStatus} is read-only for the recruiter. */
public record RecruiterProfile(
        UUID id, UUID userId, String jobTitle, String phone, ApprovalStatus approvalStatus, Instant updatedAt) {}
