package com.jobforge.backend.interview.api;

import com.jobforge.backend.interview.domain.InterviewOutcome;
import com.jobforge.backend.interview.domain.InterviewType;
import com.jobforge.backend.interview.domain.SeekerChoice;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** Request bodies of API_CONTRACT §12.7. Unknown fields are rejected by the global Jackson configuration. */
public final class InterviewRequests {

    private InterviewRequests() {}

    /** {@code POST /applications/{id}/interviews}. Everything but {@code notes} is required. */
    public record Schedule(
            @NotNull InterviewType type,
            @NotNull Instant scheduledAt,
            @NotNull @Min(15) @Max(480) Integer durationMinutes,
            @NotBlank @Size(max = 50) String timezone,
            @NotBlank @Size(max = 500) String locationOrLink,
            @Size(max = 2000) String notes) {}

    /** {@code PATCH /interviews/{id}}: absent = unchanged; blank {@code notes} clears them. */
    public record Update(
            InterviewType type,
            Instant scheduledAt,
            @Min(15) @Max(480) Integer durationMinutes,
            @Size(min = 1, max = 50) String timezone,
            @Size(min = 1, max = 500) String locationOrLink,
            @Size(max = 2000) String notes) {}

    public record Cancel(@NotBlank @Size(max = 500) String reason) {}

    public record Respond(@NotNull SeekerChoice response, @Size(max = 500) String note) {}

    public record Complete(@NotNull InterviewOutcome outcome) {}
}
