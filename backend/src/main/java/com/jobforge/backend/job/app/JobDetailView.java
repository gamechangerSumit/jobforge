package com.jobforge.backend.job.app;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.jobforge.backend.company.facade.CompanyAccessFacade.CompanyInfo;
import com.jobforge.backend.job.facade.JobViews.LocationView;
import com.jobforge.backend.job.facade.JobViews.SalaryView;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Job detail (API_CONTRACT §12.4): JobSummary fields plus the detail fields. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JobDetailView(
        UUID id,
        String slug,
        String title,
        String status,
        CompanyInfo company,
        String description,
        String requirements,
        String benefits,
        String employmentType,
        String workMode,
        String experienceLevel,
        LocationView location,
        SalaryView salary,
        boolean salaryVisible,
        int openings,
        List<SkillOut> skills,
        Instant postedAt,
        Instant expiresAt,
        Instant closedAt,
        boolean aiGenerated,
        Integer qualityScore,
        Long applicationCount,
        Boolean saved,
        Boolean applied,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    public record SkillOut(String name, String slug, boolean required) {}
}
