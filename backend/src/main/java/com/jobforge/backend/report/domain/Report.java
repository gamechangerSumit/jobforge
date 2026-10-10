package com.jobforge.backend.report.domain;

import java.time.Instant;
import java.util.UUID;

/** A user report about a piece of content or an account (core.reports). {@code details} is private reporter text. */
public record Report(
        UUID id,
        UUID reporterId,
        ReportTargetType targetType,
        UUID targetId,
        ReportReason reason,
        String details,
        ReportStatus status,
        Instant createdAt,
        Instant updatedAt) {

    @Override
    public String toString() {
        return "Report[id=" + id + ", targetType=" + targetType + ", status=" + status + "]";
    }
}
