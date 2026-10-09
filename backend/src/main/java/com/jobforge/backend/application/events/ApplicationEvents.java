package com.jobforge.backend.application.events;

import java.util.UUID;

/** Payloads of ARCHITECTURE §14 application events (topic jobforge.applications.v1). Reasons/ratings are never included. */
public final class ApplicationEvents {

    private ApplicationEvents() {}

    public record ApplicationSubmitted(UUID applicationId, UUID jobId, UUID companyId, UUID seekerUserId, String jobTitle) {}

    public record ApplicationStatusChanged(UUID applicationId, UUID jobId, UUID companyId, UUID seekerUserId, String from, String to) {}

    public record ApplicationWithdrawn(UUID applicationId, UUID jobId, UUID companyId, UUID seekerUserId) {}
}
