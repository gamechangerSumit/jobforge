package com.jobforge.backend.application.app;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.jobforge.backend.job.facade.JobViews.JobLiteView;
import com.jobforge.backend.shared.domain.UserRole;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Views returned by the application endpoints (API_CONTRACT §12.6). Never contain passwords or tokens. */
public final class ApplicationViews {

    private ApplicationViews() {}

    public record UserSummary(UUID id, String handle, String firstName, String lastName, String avatarUrl, UserRole role) {}

    public record HistoryItem(String from, String to, UUID changedBy, String reason, Instant at) {}

    /** Optional parts are null (and omitted) where they do not apply: seeker view hides rating/seeker/snapshot. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ApplicationView(
            UUID id,
            UUID jobId,
            JobLiteView job,
            UserSummary seeker,
            String status,
            String coverLetter,
            UUID resumeId,
            Integer rating,
            Instant appliedAt,
            Instant statusUpdatedAt,
            List<HistoryItem> statusHistory,
            JsonNode profileSnapshot,
            long version) {}

    public record NoteView(UUID id, UUID applicationId, UserSummary author, String body, Instant createdAt, Instant updatedAt) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CandidateView(
            UserSummary user,
            UUID profileId,
            String headline,
            String summary,
            Object location,
            String currentTitle,
            java.math.BigDecimal yearsExperience,
            Integer noticePeriodDays,
            boolean openToWork,
            String visibility,
            Object links,
            int completenessScore,
            List<?> skills) {}
}
