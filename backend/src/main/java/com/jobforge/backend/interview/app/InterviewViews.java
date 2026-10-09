package com.jobforge.backend.interview.app;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.jobforge.backend.job.facade.JobViews.JobLiteView;
import java.time.Instant;
import java.util.UUID;

/**
 * Response shapes of API_CONTRACT §12.7. Recruiter-only parts ({@code seeker}, {@code notes}) are null for the seeker
 * and omitted from the JSON. Never contains e-mail addresses, tokens or credentials.
 */
public final class InterviewViews {

    private InterviewViews() {}

    public record UserSummary(UUID id, String handle, String firstName, String lastName) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record InterviewView(
            UUID id,
            UUID applicationId,
            String applicationStatus,
            JobLiteView job,
            UserSummary seeker,
            UserSummary scheduledBy,
            String type,
            Instant scheduledAt,
            int durationMinutes,
            String timezone,
            String locationOrLink,
            String status,
            String seekerResponse,
            String seekerResponseNote,
            String cancelledReason,
            String notes,
            Instant createdAt,
            Instant updatedAt) {}
}
