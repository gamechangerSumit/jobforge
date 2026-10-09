package com.jobforge.backend.job.app;

import com.jobforge.backend.job.facade.JobViews.LocationView;
import java.time.Instant;
import java.util.UUID;

/** Row of {@code GET /recruiters/me/jobs}: company jobs in any status. */
public record RecruiterJobItem(
        UUID id,
        String title,
        String slug,
        String status,
        String workMode,
        String employmentType,
        LocationView location,
        Instant publishedAt,
        Instant expiresAt,
        long applicationCount,
        long version,
        Instant updatedAt) {}
