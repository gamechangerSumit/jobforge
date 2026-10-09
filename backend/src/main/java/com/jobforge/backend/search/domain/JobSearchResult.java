package com.jobforge.backend.search.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record JobSearchResult(
        UUID id,
        String title,
        CompanySummary company,
        LocationSummary location,
        String workMode,
        String employmentType,
        String experienceLevel,
        SalarySummary salary,
        List<String> skills,
        Instant postedAt,
        Boolean saved,
        Boolean applied,
        double relevance
) {

    public record CompanySummary(
            UUID id,
            String name,
            String slug,
            String logoUrl,
            boolean verified
    ) {
    }

    public record LocationSummary(
            String city,
            String state,
            String country
    ) {
    }

    public record SalarySummary(
            BigDecimal min,
            BigDecimal max,
            String currency,
            String period
    ) {
    }

    /** Copy with the per-caller flags filled in (anonymous callers keep {@code null}). */
    public JobSearchResult withFlags(Boolean savedFlag, Boolean appliedFlag) {
        return new JobSearchResult(id, title, company, location, workMode, employmentType, experienceLevel, salary,
                skills, postedAt, savedFlag, appliedFlag, relevance);
    }
}
