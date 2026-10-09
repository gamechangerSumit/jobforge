package com.jobforge.backend.interview.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * DATABASE_SCHEMA §4.5 interviews. {@code internalNotes} are recruiter-only. The record is immutable; the {@code with*}
 * methods build the next state which the repository persists conditionally on the previously read status.
 */
public record Interview(
        UUID id,
        UUID applicationId,
        UUID scheduledBy,
        InterviewType type,
        Instant scheduledAt,
        int durationMinutes,
        String timezone,
        String locationOrLink,
        InterviewStatus status,
        InterviewResponse seekerResponse,
        String seekerResponseNote,
        String internalNotes,
        String cancelledReason,
        Instant createdAt,
        Instant updatedAt) {

    public Instant endsAt() {
        return scheduledAt.plusSeconds(durationMinutes * 60L);
    }

    /** New (or changed) schedule: back to SCHEDULED with a pending, cleared seeker response. */
    public Interview rescheduled(InterviewType newType, Instant newScheduledAt, int newDuration, String newTimezone,
            String newLocationOrLink, String newInternalNotes) {
        return new Interview(id, applicationId, scheduledBy, newType, newScheduledAt, newDuration, newTimezone,
                newLocationOrLink, InterviewStatus.SCHEDULED, InterviewResponse.PENDING, null, newInternalNotes,
                cancelledReason, createdAt, updatedAt);
    }

    public Interview withInternalNotes(String notes) {
        return new Interview(id, applicationId, scheduledBy, type, scheduledAt, durationMinutes, timezone, locationOrLink,
                status, seekerResponse, seekerResponseNote, notes, cancelledReason, createdAt, updatedAt);
    }

    public Interview responded(InterviewStatus newStatus, InterviewResponse response, String note) {
        return new Interview(id, applicationId, scheduledBy, type, scheduledAt, durationMinutes, timezone, locationOrLink,
                newStatus, response, note, internalNotes, cancelledReason, createdAt, updatedAt);
    }

    public Interview cancelled(String reason) {
        return new Interview(id, applicationId, scheduledBy, type, scheduledAt, durationMinutes, timezone, locationOrLink,
                InterviewStatus.CANCELLED, seekerResponse, seekerResponseNote, internalNotes, reason, createdAt, updatedAt);
    }

    public Interview withStatus(InterviewStatus newStatus) {
        return new Interview(id, applicationId, scheduledBy, type, scheduledAt, durationMinutes, timezone, locationOrLink,
                newStatus, seekerResponse, seekerResponseNote, internalNotes, cancelledReason, createdAt, updatedAt);
    }
}
