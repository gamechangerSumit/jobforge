package com.jobforge.backend.profile.app;

import com.jobforge.backend.profile.domain.ExpectedSalary;
import com.jobforge.backend.profile.domain.Links;
import com.jobforge.backend.profile.domain.Location;
import com.jobforge.backend.profile.domain.ProfileVisibility;
import java.math.BigDecimal;

/** The editable set of a seeker profile (PUT = replace). */
public record SeekerProfileUpdate(
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
        Links links) {}
