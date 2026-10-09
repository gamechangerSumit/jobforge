package com.jobforge.backend.interview.events;

import java.util.UUID;

/**
 * Payloads of ARCHITECTURE §14 interview events (topic jobforge.interviews.v1). They carry ids, the job title and
 * schedule metadata only: never notes, reasons or response notes (free text) and never e-mail addresses.
 * {@code scheduledAt} is an ISO-8601 UTC string.
 */
public final class InterviewEvents {

    private InterviewEvents() {}

    public record InterviewScheduled(UUID interviewId, UUID applicationId, UUID jobId, UUID companyId, UUID seekerUserId,
            UUID recruiterUserId, String jobTitle, String type, String scheduledAt, int durationMinutes) {}

    public record InterviewUpdated(UUID interviewId, UUID applicationId, UUID jobId, UUID companyId, UUID seekerUserId,
            UUID recruiterUserId, String jobTitle, String type, String scheduledAt, int durationMinutes) {}

    /**
     * {@code cause}: RECRUITER (cancelled by a recruiter), APPLICATION_WITHDRAWN or APPLICATION_REJECTED (cancelled by the
     * system because the application closed).
     */
    public record InterviewCancelled(UUID interviewId, UUID applicationId, UUID jobId, UUID companyId, UUID seekerUserId,
            UUID recruiterUserId, String jobTitle, String type, String scheduledAt, int durationMinutes, String cause) {}

    /** {@code response} is CONFIRMED or DECLINED. */
    public record InterviewResponded(UUID interviewId, UUID applicationId, UUID jobId, UUID companyId, UUID seekerUserId,
            UUID recruiterUserId, String jobTitle, String type, String scheduledAt, int durationMinutes, String response) {}
}
