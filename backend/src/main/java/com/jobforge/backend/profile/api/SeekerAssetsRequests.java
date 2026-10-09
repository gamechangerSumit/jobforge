package com.jobforge.backend.profile.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Request DTOs (API_CONTRACT §12.2, limits §10). */
public final class SeekerAssetsRequests {

    private SeekerAssetsRequests() {}

    public record Skill(
            @NotBlank @Size(max = 50) String skill,
            @NotBlank String proficiency,
            @DecimalMin("0") @DecimalMax("60") BigDecimal years) {}

    public record Skills(@NotNull @Size(max = 50) List<@Valid Skill> skills) {}

    public record EducationCreate(
            @NotBlank @Size(max = 150) String institution,
            @NotBlank @Size(max = 100) String degree,
            @Size(max = 100) String fieldOfStudy,
            @NotNull LocalDate startDate,
            LocalDate endDate,
            @Size(max = 30) String grade,
            @Size(max = 1000) String description) {}

    public record EducationPatch(
            @Size(min = 1, max = 150) String institution,
            @Size(min = 1, max = 100) String degree,
            @Size(max = 100) String fieldOfStudy,
            LocalDate startDate,
            LocalDate endDate,
            /** true clears a stored endDate (null alone means unchanged). */
            Boolean clearEndDate,
            @Size(max = 30) String grade,
            @Size(max = 1000) String description) {}

    public record ExperienceCreate(
            @NotBlank @Size(max = 120) String title,
            @NotBlank @Size(max = 150) String companyName,
            @Size(max = 120) String location,
            String employmentType,
            @NotNull LocalDate startDate,
            LocalDate endDate,
            Boolean current,
            @Size(max = 2000) String description) {}

    public record ExperiencePatch(
            @Size(min = 1, max = 120) String title,
            @Size(min = 1, max = 150) String companyName,
            @Size(max = 120) String location,
            String employmentType,
            LocalDate startDate,
            LocalDate endDate,
            /** true clears a stored endDate (null alone means unchanged). */
            Boolean clearEndDate,
            Boolean current,
            @Size(max = 2000) String description) {}
}
