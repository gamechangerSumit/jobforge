package com.jobforge.backend.profile.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** DATABASE_SCHEMA §4.2 seeker_profiles (+ seeker_skills, read-only here). */
public record SeekerProfile(
        UUID id,
        UUID userId,
        String headline,
        String summary,
        String phone,
        Location location,
        String currentTitle,
        BigDecimal yearsExperience,
        ExpectedSalary expectedSalary,
        Integer noticePeriodDays,
        boolean openToWork,
        ProfileVisibility visibility,
        Links links,
        int completenessScore,
        List<SkillEntry> skills,
        long version,
        Instant updatedAt) {}
