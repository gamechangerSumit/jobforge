package com.jobforge.backend.application.domain;

import java.time.Instant;
import java.util.UUID;

public record StatusHistoryEntry(
        UUID id, UUID applicationId, ApplicationStatus from, ApplicationStatus to, UUID changedBy, String reason, Instant createdAt) {}
