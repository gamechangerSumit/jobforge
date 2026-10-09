package com.jobforge.backend.job.api;

import com.jobforge.backend.job.app.JobDraft;
import com.jobforge.backend.job.app.JobDraft.SkillInput;
import com.jobforge.backend.job.domain.EmploymentType;
import com.jobforge.backend.job.domain.ExperienceLevel;
import com.jobforge.backend.job.domain.JobLocation;
import com.jobforge.backend.job.domain.JobSalary;
import com.jobforge.backend.job.domain.SalaryPeriod;
import com.jobforge.backend.job.domain.WorkMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Request bodies of API_CONTRACT §12.4. Limits mirror §10.
 */
public final class JobRequests {

    private JobRequests() {
    }

    public record LocationDto(
            @Size(max = 80) String city,
            @Size(max = 80) String state,
            @Pattern(regexp = "^[A-Z]{2}$") String country) {

        JobLocation toDomain() {
            return new JobLocation(city, state, country);
        }
    }

    public record SalaryDto(
            @PositiveOrZero Long min,
            @PositiveOrZero Long max,
            @Pattern(regexp = "^[A-Z]{3}$") String currency,
            SalaryPeriod period) {

        JobSalary toDomain() {
            return new JobSalary(min, max, currency, period);
        }
    }

    public record SkillDto(
            @NotBlank @Size(max = 50) String name,
            Boolean required) {

        SkillInput toInput() {
            return new SkillInput(name, required == null || required);
        }
    }

    /**
     * Converts API skill DTOs into domain/application skill inputs.
     */
    static List<SkillInput> toSkillInputs(List<SkillDto> dtos) {
        return dtos == null
                ? null
                : dtos.stream()
                  .map(SkillDto::toInput)
                  .toList();
    }

    public record Create(
            @NotBlank @Size(min = 3, max = 120) String title,
            @NotBlank @Size(min = 50, max = 10000) String description,
            @Size(max = 5000) String requirements,
            @Size(max = 5000) String benefits,
            @NotNull EmploymentType employmentType,
            @NotNull WorkMode workMode,
            @NotNull ExperienceLevel experienceLevel,
            @Valid LocationDto location,
            @Valid SalaryDto salary,
            Boolean salaryVisible,
            @Min(1) @Max(1000) Integer openings,
            Instant expiresAt,
            @Size(max = 20) List<@Valid SkillDto> skills,
            UUID aiRequestId) {

        JobDraft toDraft() {
            return new JobDraft(
                    title,
                    description,
                    requirements,
                    benefits,
                    employmentType,
                    workMode,
                    experienceLevel,
                    location == null ? null : location.toDomain(),
                    salary == null ? null : salary.toDomain(),
                    salaryVisible,
                    openings,
                    expiresAt,
                    toSkillInputs(skills),
                    aiRequestId,
                    null
            );
        }
    }

    /**
     * PATCH body (JSON merge patch).
     *
     * A class with setters so we can tell "absent" from "null":
     * absent = unchanged,
     * null = clear (nullable fields).
     *
     * Unknown fields are still rejected by Jackson.
     * {@code aiRequestId} is not patchable.
     */
    public static class Patch {

        private final Set<String> present = new HashSet<>();

        @Size(min = 3, max = 120)
        private String title;

        @Size(min = 50, max = 10000)
        private String description;

        @Size(max = 5000)
        private String requirements;

        @Size(max = 5000)
        private String benefits;

        private EmploymentType employmentType;
        private WorkMode workMode;
        private ExperienceLevel experienceLevel;

        @Valid
        private LocationDto location;

        @Valid
        private SalaryDto salary;

        private Boolean salaryVisible;

        @Min(1)
        @Max(1000)
        private Integer openings;

        private Instant expiresAt;

        @Size(max = 20)
        private List<@Valid SkillDto> skills;

        public void setTitle(String v) {
            title = v;
            present.add("title");
        }

        public void setDescription(String v) {
            description = v;
            present.add("description");
        }

        public void setRequirements(String v) {
            requirements = v;
            present.add("requirements");
        }

        public void setBenefits(String v) {
            benefits = v;
            present.add("benefits");
        }

        public void setEmploymentType(EmploymentType v) {
            employmentType = v;
            present.add("employmentType");
        }

        public void setWorkMode(WorkMode v) {
            workMode = v;
            present.add("workMode");
        }

        public void setExperienceLevel(ExperienceLevel v) {
            experienceLevel = v;
            present.add("experienceLevel");
        }

        public void setLocation(LocationDto v) {
            location = v;
            present.add("location");
        }

        public void setSalary(SalaryDto v) {
            salary = v;
            present.add("salary");
        }

        public void setSalaryVisible(Boolean v) {
            salaryVisible = v;
            present.add("salaryVisible");
        }

        public void setOpenings(Integer v) {
            openings = v;
            present.add("openings");
        }

        public void setExpiresAt(Instant v) {
            expiresAt = v;
            present.add("expiresAt");
        }

        public void setSkills(List<SkillDto> v) {
            skills = v;
            present.add("skills");
        }

        JobDraft toDraft() {
            return new JobDraft(
                    title,
                    description,
                    requirements,
                    benefits,
                    employmentType,
                    workMode,
                    experienceLevel,
                    location == null ? null : location.toDomain(),
                    salary == null ? null : salary.toDomain(),
                    salaryVisible,
                    openings,
                    expiresAt,
                    toSkillInputs(skills),
                    null,
                    Set.copyOf(present)
            );
        }
    }
}