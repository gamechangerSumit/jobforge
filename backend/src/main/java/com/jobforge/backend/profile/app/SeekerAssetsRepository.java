package com.jobforge.backend.profile.app;

import com.jobforge.backend.profile.app.SeekerAssets.EducationInput;
import com.jobforge.backend.profile.app.SeekerAssets.EducationItem;
import com.jobforge.backend.profile.app.SeekerAssets.ExperienceInput;
import com.jobforge.backend.profile.app.SeekerAssets.ExperienceItem;
import com.jobforge.backend.profile.app.SeekerAssets.ResumeFile;
import com.jobforge.backend.profile.app.SeekerAssets.ResumeItem;
import com.jobforge.backend.profile.app.SeekerAssets.SkillItem;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Port for seeker sub-records. Every method is scoped by the seeker's profile id (ownership by construction). */
public interface SeekerAssetsRepository {

    Optional<UUID> profileIdOfUser(UUID userId);

    // skills
    List<SkillItem> skills(UUID profileId);

    /** Replaces the whole skill set; keys of {@code skillIds} are lower-cased skill names. */
    void replaceSkills(UUID profileId, Map<String, UUID> skillIds, List<SkillItem> items);

    // education
    List<EducationItem> education(UUID profileId);

    EducationItem insertEducation(UUID profileId, UUID id, EducationInput input);

    Optional<EducationItem> updateEducation(UUID profileId, UUID id, EducationInput input);

    boolean deleteEducation(UUID profileId, UUID id);

    // experience
    List<ExperienceItem> experience(UUID profileId);

    ExperienceItem insertExperience(UUID profileId, UUID id, ExperienceInput input);

    Optional<ExperienceItem> updateExperience(UUID profileId, UUID id, ExperienceInput input);

    boolean deleteExperience(UUID profileId, UUID id);

    // resumes
    List<ResumeItem> resumes(UUID profileId);

    int activeResumeCount(UUID profileId);

    ResumeItem insertResume(UUID profileId, UUID id, String filename, String storageKey, String contentType, int size,
            String sha256, boolean primary, Instant now);

    Optional<ResumeFile> findResume(UUID profileId, UUID id);

    boolean setPrimaryResume(UUID profileId, UUID id);

    /** Soft-deletes the resume; promotes the newest remaining one to primary when the primary was removed. */
    boolean softDeleteResume(UUID profileId, UUID id, Instant now);
}
