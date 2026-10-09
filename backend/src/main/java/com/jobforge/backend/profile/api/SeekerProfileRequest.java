package com.jobforge.backend.profile.api;

import com.jobforge.backend.profile.domain.ProfileVisibility;
import com.jobforge.backend.profile.domain.SalaryPeriod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * PUT /seekers/me/profile body — the editable set (API_CONTRACT §12.2, limits §10). PUT = replace: absent
 * optional fields are cleared; absent {@code openToWork} → true, absent {@code visibility} → RECRUITERS_ONLY.
 */
public record SeekerProfileRequest(
        @Size(max = 120) String headline,
        @Size(max = 2000) String summary,
        @Pattern(regexp = "^\\+[1-9]\\d{1,14}$") String phone,
        @Valid LocationDto location,
        @Size(max = 120) String currentTitle,
        @Min(0) @Max(60) @Digits(integer = 2, fraction = 1) BigDecimal yearsExperience,
        @Valid SalaryDto expectedSalary,
        @Min(0) @Max(365) Integer noticePeriodDays,
        Boolean openToWork,
        ProfileVisibility visibility,
        @Valid LinksDto links) {

    public record LocationDto(
            @Size(max = 80) String city,
            @Size(max = 80) String state,
            @Pattern(regexp = "^[A-Z]{2}$") String country) {}

    public record SalaryDto(
            @PositiveOrZero Long min,
            @PositiveOrZero Long max,
            @Pattern(regexp = "^[A-Z]{3}$") String currency,
            SalaryPeriod period) {}

    public record LinksDto(
            @Size(max = 255) @Pattern(regexp = "^https://\\S+$") String linkedin,
            @Size(max = 255) @Pattern(regexp = "^https://\\S+$") String github,
            @Size(max = 255) @Pattern(regexp = "^https://\\S+$") String portfolio) {}
}
