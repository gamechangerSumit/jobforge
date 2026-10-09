package com.jobforge.backend.application.domain;

import java.time.Instant;
import java.util.UUID;

/** DATABASE_SCHEMA §4.5 applications. {@code profileSnapshot} is the immutable JSON captured at submission. */
public record Application(
        UUID id,
        UUID jobId,
        UUID seekerUserId,
        UUID resumeId,
        String coverLetter,
        ApplicationStatus status,
        String profileSnapshot,
        Integer rating,
        Instant appliedAt,
        Instant statusUpdatedAt,
        Instant withdrawnAt,
        long version,
        Instant createdAt,
        Instant updatedAt) {}
