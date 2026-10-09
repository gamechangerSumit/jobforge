package com.jobforge.backend.job.app;

import com.jobforge.backend.job.domain.EmploymentType;
import com.jobforge.backend.job.domain.ExperienceLevel;
import com.jobforge.backend.job.domain.JobLocation;
import com.jobforge.backend.job.domain.JobSalary;
import com.jobforge.backend.job.domain.WorkMode;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Input of create and PATCH. For PATCH, {@code present} names the fields that were sent (null value = clear). */
public record JobDraft(
        String title,
        String description,
        String requirements,
        String benefits,
        EmploymentType employmentType,
        WorkMode workMode,
        ExperienceLevel experienceLevel,
        JobLocation location,
        JobSalary salary,
        Boolean salaryVisible,
        Integer openings,
        Instant expiresAt,
        List<SkillInput> skills,
        UUID aiRequestId,
        Set<String> present) {

    public record SkillInput(String name, boolean required) {}

    public boolean has(String field) {
        return present != null && present.contains(field);
    }
}
