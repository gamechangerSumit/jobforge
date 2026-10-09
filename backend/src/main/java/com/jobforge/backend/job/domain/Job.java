package com.jobforge.backend.job.domain;

import java.time.Instant;
import java.util.UUID;

/** DATABASE_SCHEMA §4.4 jobs. Immutable; state changes produce new instances. */
public record Job(
        UUID id,
        UUID companyId,
        UUID createdBy,
        String slug,
        JobContent content,
        JobStatus status,
        Instant publishedAt,
        Instant closedAt,
        boolean aiGenerated,
        UUID aiRequestId,
        Integer qualityScore,
        UUID removedBy,
        String removedReason,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    public Job withContent(JobContent value) {
        return new Job(id, companyId, createdBy, slug, value, status, publishedAt, closedAt, aiGenerated, aiRequestId,
                qualityScore, removedBy, removedReason, version, createdAt, updatedAt);
    }

    public Job withStatus(JobStatus value, Instant newPublishedAt, Instant newClosedAt) {
        return new Job(id, companyId, createdBy, slug, content, value, newPublishedAt, newClosedAt, aiGenerated,
                aiRequestId, qualityScore, removedBy, removedReason, version, createdAt, updatedAt);
    }

    public Job withRemoval(JobStatus value, UUID by, String reason) {
        return new Job(id, companyId, createdBy, slug, content, value, publishedAt, closedAt, aiGenerated, aiRequestId,
                qualityScore, by, reason, version, createdAt, updatedAt);
    }

    /** Publicly listable: PUBLISHED and not past {@code expires_at}. */
    public boolean isOpenAt(Instant now) {
        return status == JobStatus.PUBLISHED && (content.expiresAt() == null || content.expiresAt().isAfter(now));
    }
}
