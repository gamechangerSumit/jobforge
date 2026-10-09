package com.jobforge.backend.application.api;

import com.jobforge.backend.application.domain.ApplicationStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Request bodies of API_CONTRACT §12.6. Limits mirror §10. */
public final class ApplicationRequests {

    private ApplicationRequests() {}

    public record Apply(@NotNull UUID resumeId, @Size(max = 5000) String coverLetter) {}

    public record StatusChange(@NotNull ApplicationStatus status, @Size(max = 500) String reason) {}

    public record Rating(@NotNull @Min(1) @Max(5) Integer rating) {}

    public record Note(@NotBlank @Size(max = 2000) String body) {}
}
