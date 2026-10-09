package com.jobforge.backend.profile.api;

import com.jobforge.backend.profile.domain.ExpectedSalary;
import com.jobforge.backend.profile.domain.Links;
import com.jobforge.backend.profile.domain.Location;
import com.jobforge.backend.profile.domain.ProfileVisibility;
import com.jobforge.backend.profile.domain.SeekerProfile;
import com.jobforge.backend.profile.domain.SkillEntry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** SeekerProfile DTO (API_CONTRACT §12.2). */
public record SeekerProfileResponse(
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
        Instant updatedAt) {

    static SeekerProfileResponse from(SeekerProfile p) {
        return new SeekerProfileResponse(p.id(), p.userId(), p.headline(), p.summary(), p.phone(), p.location(),
                p.currentTitle(), p.yearsExperience(), p.expectedSalary(), p.noticePeriodDays(), p.openToWork(),
                p.visibility(), p.links(), p.completenessScore(), p.skills(), p.version(), p.updatedAt());
    }
}
