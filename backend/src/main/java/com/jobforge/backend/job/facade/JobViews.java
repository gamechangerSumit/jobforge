package com.jobforge.backend.job.facade;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.jobforge.backend.company.facade.CompanyAccessFacade.CompanyInfo;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Read models shared with other modules (and serialized by the API). Enum values travel as UPPER_SNAKE strings. */
public final class JobViews {

    private JobViews() {}

    public record LocationView(String city, String state, String country) {}

    /** Minimal job data for the report module: no description, requirements or salary. */
    public record JobReportView(UUID id, String title, String status, UUID companyId, UUID createdBy, boolean publiclyVisible) {}

    public record SalaryView(Long min, Long max, String currency, String period) {}

    /** JobSummary (API_CONTRACT §11). {@code saved}/{@code applied} are present only for authenticated seekers. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record JobSummaryView(
            UUID id,
            String title,
            CompanyInfo company,
            LocationView location,
            String workMode,
            String employmentType,
            String experienceLevel,
            SalaryView salary,
            List<String> skills,
            Instant postedAt,
            Boolean saved,
            Boolean applied) {}

    /** What the application module needs to decide whether a job can be applied to. */
    public record JobApplyView(UUID id, UUID companyId, String title, String status, Instant expiresAt) {}

    /** Minimal job reference embedded in applications; resolved even if the job is no longer public. */
    public record JobLiteView(UUID id, String title, CompanyInfo company, LocationView location) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record JobAdminView(
            UUID id,
            String title,
            String slug,
            String status,
            CompanyInfo company,
            UUID createdBy,
            Instant publishedAt,
            Instant expiresAt,
            String removedReason,
            long version,
            Instant createdAt,
            Instant updatedAt) {}
}
