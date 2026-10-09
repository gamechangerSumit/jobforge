package com.jobforge.backend.profile.app;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Read/command models for seeker skills, education, experience and resumes (API_CONTRACT §12.2). */
public final class SeekerAssets {

    private SeekerAssets() {}

    public record SkillItem(String skill, String proficiency, BigDecimal years) {}

    public record EducationItem(UUID id, String institution, String degree, String fieldOfStudy, LocalDate startDate,
            LocalDate endDate, String grade, String description) {}

    public record EducationInput(String institution, String degree, String fieldOfStudy, LocalDate startDate,
            LocalDate endDate, String grade, String description) {}

    public record ExperienceItem(UUID id, String title, String companyName, String location, String employmentType,
            LocalDate startDate, LocalDate endDate, boolean current, String description) {}

    public record ExperienceInput(String title, String companyName, String location, String employmentType,
            LocalDate startDate, LocalDate endDate, boolean current, String description) {}

    public record ResumeItem(UUID id, String fileName, String contentType, int sizeBytes, boolean primary,
            Instant createdAt) {}

    /** Internal view used for downloads; the storage key never leaves the module. */
    public record ResumeFile(UUID id, String originalFilename, String storageKey, String contentType, int sizeBytes) {}
}
