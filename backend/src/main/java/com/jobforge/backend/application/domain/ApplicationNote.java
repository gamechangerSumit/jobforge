package com.jobforge.backend.application.domain;

import java.time.Instant;
import java.util.UUID;

public record ApplicationNote(UUID id, UUID applicationId, UUID authorUserId, String body, Instant createdAt, Instant updatedAt) {}
