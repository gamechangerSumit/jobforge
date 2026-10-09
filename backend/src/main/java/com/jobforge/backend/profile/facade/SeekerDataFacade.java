package com.jobforge.backend.profile.facade;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Seeker data needed by applications: immutable snapshot, resume ownership, recruiter-facing candidate view. */
public interface SeekerDataFacade {

    /** The only profile data copied into {@code applications.profile_snapshot} (no contact details). */
    record ProfileSnapshot(String headline, List<String> skills, BigDecimal yearsExperience) {}

    record LocationView(String city, String state, String country) {}

    record LinksView(String linkedin, String github, String portfolio) {}

    record SkillView(String skill, String proficiency, BigDecimal years) {}

    /** Candidate view for recruiters/admins. Phone and expected salary are deliberately excluded (contract TBD, REQ). */
    record CandidateProfile(
            UUID id,
            UUID userId,
            String headline,
            String summary,
            LocationView location,
            String currentTitle,
            BigDecimal yearsExperience,
            Integer noticePeriodDays,
            boolean openToWork,
            String visibility,
            LinksView links,
            int completenessScore,
            List<SkillView> skills) {}

    Optional<ProfileSnapshot> snapshot(UUID seekerUserId);

    /** The resume belongs to the seeker and is not soft-deleted. */
    boolean ownsActiveResume(UUID seekerUserId, UUID resumeId);

    Optional<CandidateProfile> candidate(UUID seekerUserId);
}
