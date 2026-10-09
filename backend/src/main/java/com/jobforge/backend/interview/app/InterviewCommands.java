package com.jobforge.backend.interview.app;

import com.jobforge.backend.interview.domain.InterviewOutcome;
import com.jobforge.backend.interview.domain.InterviewType;
import com.jobforge.backend.interview.domain.SeekerChoice;
import java.time.Instant;

/** Commands handed from the controller to {@link InterviewService} (no HTTP types). */
public final class InterviewCommands {

    private InterviewCommands() {}

    public record Schedule(InterviewType type, Instant scheduledAt, int durationMinutes, String timezone, String locationOrLink,
            String notes) {}

    /** Merge-patch: {@code null} = unchanged; blank {@code notes} clears them. */
    public record Update(InterviewType type, Instant scheduledAt, Integer durationMinutes, String timezone, String locationOrLink,
            String notes) {}

    public record Respond(SeekerChoice response, String note) {}

    public record Cancel(String reason) {}

    public record Complete(InterviewOutcome outcome) {}

    /** Optional list filters (all nullable). */
    public record Filter(java.util.UUID applicationId, com.jobforge.backend.interview.domain.InterviewStatus status, Instant from, Instant to) {}
}
