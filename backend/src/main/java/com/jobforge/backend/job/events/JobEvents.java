package com.jobforge.backend.job.events;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Payloads of ARCHITECTURE §14 job events (topic jobforge.jobs.v1). No free text beyond the title. */
public final class JobEvents {

    private JobEvents() {}

    public record JobPublished(UUID jobId, UUID companyId, String title, String slug, Instant publishedAt, Instant expiresAt) {}

    public record JobUnpublished(UUID jobId, UUID companyId) {}

    public record JobClosed(UUID jobId, UUID companyId) {}

    public record JobExpired(UUID jobId, UUID companyId) {}

    public record JobRemoved(UUID jobId, UUID companyId) {}

    public record JobUpdated(UUID jobId, UUID companyId, List<String> changedFields) {}
}
