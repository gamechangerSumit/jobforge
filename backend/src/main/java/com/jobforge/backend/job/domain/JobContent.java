package com.jobforge.backend.job.domain;

import java.time.Instant;
import java.util.List;

/** The recruiter-editable part of a job (PATCH /jobs/{id}). */
public record JobContent(
        String title,
        String description,
        String requirements,
        String benefits,
        EmploymentType employmentType,
        WorkMode workMode,
        ExperienceLevel experienceLevel,
        JobLocation location,
        JobSalary salary,
        boolean salaryVisible,
        int openings,
        Instant expiresAt,
        List<JobSkill> skills) {

    public JobContent withExpiresAt(Instant value) {
        return new JobContent(title, description, requirements, benefits, employmentType, workMode, experienceLevel,
                location, salary, salaryVisible, openings, value, skills);
    }

    public JobContent withSkills(List<JobSkill> value) {
        return new JobContent(title, description, requirements, benefits, employmentType, workMode, experienceLevel,
                location, salary, salaryVisible, openings, expiresAt, value);
    }
}
