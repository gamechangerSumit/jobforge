package com.jobforge.backend.profile.app;

import com.jobforge.backend.profile.app.SeekerAssets.EducationInput;
import com.jobforge.backend.profile.app.SeekerAssets.EducationItem;
import com.jobforge.backend.profile.app.SeekerAssets.ExperienceInput;
import com.jobforge.backend.profile.app.SeekerAssets.ExperienceItem;
import com.jobforge.backend.profile.app.SeekerAssets.ResumeFile;
import com.jobforge.backend.profile.app.SeekerAssets.ResumeItem;
import com.jobforge.backend.profile.app.SeekerAssets.SkillItem;
import com.jobforge.backend.profile.domain.SkillProficiency;
import com.jobforge.backend.profile.facade.SkillCatalogFacade;
import com.jobforge.backend.profile.facade.SkillCatalogFacade.SkillRef;
import com.jobforge.backend.shared.error.BusinessRuleException;
import com.jobforge.backend.shared.error.FieldErrorDetail;
import com.jobforge.backend.shared.error.ResourceNotFoundException;
import com.jobforge.backend.shared.error.ValidationFailedException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Seeker skills / education / experience / resumes (API_CONTRACT §12.2). All access is scoped to the caller. */
@Service
public class SeekerAssetsService {

    static final int MAX_RESUME_BYTES = 5 * 1024 * 1024;
    static final int MAX_RESUMES = 10;
    static final int MAX_SKILLS = 50;
    static final String PDF = "application/pdf";
    static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private final SeekerAssetsRepository repo;
    private final SeekerProfileRepository profiles;
    private final SkillCatalogFacade catalog;
    private final ResumeStorage storage;
    private final Clock clock;

    public SeekerAssetsService(SeekerAssetsRepository repo, SeekerProfileRepository profiles,
            SkillCatalogFacade catalog, ResumeStorage storage, Clock clock) {
        this.repo = repo;
        this.profiles = profiles;
        this.catalog = catalog;
        this.storage = storage;
        this.clock = clock;
    }

    private UUID profile(UUID userId) {
        return repo.profileIdOfUser(userId).orElseThrow(() -> new ResourceNotFoundException("Profile not found."));
    }

    // ------------------------------------------------------------------ skills

    @Transactional(readOnly = true)
    public List<SkillItem> skills(UUID userId) {
        return repo.skills(profile(userId));
    }

    @Transactional
    public List<SkillItem> replaceSkills(UUID userId, List<SkillItem> items) {
        UUID profileId = profile(userId);
        List<FieldErrorDetail> problems = new ArrayList<>();
        if (items.size() > MAX_SKILLS) {
            problems.add(new FieldErrorDetail("skills", "SIZE", "at most " + MAX_SKILLS + " skills are allowed"));
        }
        Set<String> seen = new HashSet<>();
        List<SkillItem> normalized = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            SkillItem item = items.get(i);
            String name = item.skill() == null ? "" : item.skill().trim().replaceAll("\\s+", " ");
            if (name.isEmpty() || name.length() > 50) {
                problems.add(new FieldErrorDetail("[" + i + "].skill", "SIZE", "must be 1-50 characters"));
                continue;
            }
            SkillProficiency proficiency;
            try {
                proficiency = SkillProficiency.valueOf(String.valueOf(item.proficiency()));
            } catch (IllegalArgumentException e) {
                problems.add(new FieldErrorDetail("[" + i + "].proficiency", "INVALID_ENUM", "must be one of the allowed values"));
                continue;
            }
            if (item.years() != null && (item.years().signum() < 0 || item.years().compareTo(new BigDecimal("60")) > 0)) {
                problems.add(new FieldErrorDetail("[" + i + "].years", "RANGE", "must be between 0 and 60"));
                continue;
            }
            if (seen.add(name.toLowerCase(Locale.ROOT))) {
                normalized.add(new SkillItem(name, proficiency.name(), item.years()));
            }
        }
        if (!problems.isEmpty()) {
            throw new ValidationFailedException(problems);
        }
        Map<String, SkillRef> refs = catalog.resolveOrCreate(normalized.stream().map(SkillItem::skill).toList());
        Map<String, UUID> ids = new java.util.HashMap<>();
        refs.forEach((k, v) -> ids.put(k, v.id()));
        repo.replaceSkills(profileId, ids, normalized);
        profiles.recalculateCompleteness(profileId);
        return repo.skills(profileId);
    }

    // --------------------------------------------------------------- education

    @Transactional(readOnly = true)
    public List<EducationItem> education(UUID userId) {
        return repo.education(profile(userId));
    }

    @Transactional
    public EducationItem addEducation(UUID userId, EducationInput in) {
        UUID profileId = profile(userId);
        validateDates("endDate", in.startDate(), in.endDate());
        EducationItem created = repo.insertEducation(profileId, UUID.randomUUID(), in);
        profiles.recalculateCompleteness(profileId);
        return created;
    }

    /** PATCH semantics: {@code null} keeps the stored value; a blank optional text field clears it. */
    @Transactional
    public EducationItem patchEducation(UUID userId, UUID id, EducationInput patch, boolean clearEndDate) {
        UUID profileId = profile(userId);
        EducationItem current = repo.education(profileId).stream().filter(e -> e.id().equals(id)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Education entry not found."));
        EducationInput merged = new EducationInput(
                or(patch.institution(), current.institution()), or(patch.degree(), current.degree()),
                orClear(patch.fieldOfStudy(), current.fieldOfStudy()), or(patch.startDate(), current.startDate()),
                clearEndDate ? null : or(patch.endDate(), current.endDate()), orClear(patch.grade(), current.grade()),
                orClear(patch.description(), current.description()));
        validateDates("endDate", merged.startDate(), merged.endDate());
        return repo.updateEducation(profileId, id, merged)
                .orElseThrow(() -> new ResourceNotFoundException("Education entry not found."));
    }

    @Transactional
    public void deleteEducation(UUID userId, UUID id) {
        UUID profileId = profile(userId);
        if (!repo.deleteEducation(profileId, id)) {
            throw new ResourceNotFoundException("Education entry not found.");
        }
        profiles.recalculateCompleteness(profileId);
    }

    // -------------------------------------------------------------- experience

    @Transactional(readOnly = true)
    public List<ExperienceItem> experience(UUID userId) {
        return repo.experience(profile(userId));
    }

    @Transactional
    public ExperienceItem addExperience(UUID userId, ExperienceInput in) {
        UUID profileId = profile(userId);
        validateExperience(in);
        ExperienceItem created = repo.insertExperience(profileId, UUID.randomUUID(), in);
        profiles.recalculateCompleteness(profileId);
        return created;
    }

    @Transactional
    public ExperienceItem patchExperience(UUID userId, UUID id, ExperienceInput patch, Boolean currentPatch,
            boolean clearEndDate) {
        UUID profileId = profile(userId);
        ExperienceItem cur = repo.experience(profileId).stream().filter(e -> e.id().equals(id)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Experience entry not found."));
        boolean current = currentPatch == null ? cur.current() : currentPatch;
        ExperienceInput merged = new ExperienceInput(or(patch.title(), cur.title()),
                or(patch.companyName(), cur.companyName()), orClear(patch.location(), cur.location()),
                orClear(patch.employmentType(), cur.employmentType()), or(patch.startDate(), cur.startDate()),
                current || clearEndDate ? null : or(patch.endDate(), cur.endDate()), current,
                orClear(patch.description(), cur.description()));
        validateExperience(merged);
        return repo.updateExperience(profileId, id, merged)
                .orElseThrow(() -> new ResourceNotFoundException("Experience entry not found."));
    }

    @Transactional
    public void deleteExperience(UUID userId, UUID id) {
        UUID profileId = profile(userId);
        if (!repo.deleteExperience(profileId, id)) {
            throw new ResourceNotFoundException("Experience entry not found.");
        }
        profiles.recalculateCompleteness(profileId);
    }

    private static void validateExperience(ExperienceInput in) {
        validateDates("endDate", in.startDate(), in.endDate());
        if (in.current() && in.endDate() != null) {
            throw ValidationFailedException.of("endDate", "INVALID", "must be empty for a current position");
        }
    }

    private static void validateDates(String field, java.time.LocalDate start, java.time.LocalDate end) {
        if (start == null) {
            throw ValidationFailedException.of("startDate", "NOT_NULL", "is required");
        }
        if (end != null && end.isBefore(start)) {
            throw ValidationFailedException.of(field, "MIN", "must not be before startDate");
        }
    }

    private static <T> T or(T value, T fallback) {
        return value != null ? value : fallback;
    }

    /**
     * PATCH helper for optional text fields: {@code null} keeps the stored value, a blank string clears it
     * (contract change REQ-20261009), anything else replaces it.
     */
    private static String orClear(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        return value.isBlank() ? null : value;
    }

    // ----------------------------------------------------------------- resumes

    @Transactional(readOnly = true)
    public List<ResumeItem> resumes(UUID userId) {
        return repo.resumes(profile(userId));
    }

    @Transactional
    public ResumeItem uploadResume(UUID userId, String originalName, byte[] content) {
        UUID profileId = profile(userId);
        if (content == null || content.length == 0) {
            throw ValidationFailedException.of("file", "NOT_BLANK", "must not be empty");
        }
        if (content.length > MAX_RESUME_BYTES) {
            throw ValidationFailedException.of("file", "MAX", "must be at most 5 MB");
        }
        String contentType = sniff(content);
        if (contentType == null) {
            throw ValidationFailedException.of("file", "INVALID_TYPE", "only PDF and DOCX files are accepted");
        }
        if (repo.activeResumeCount(profileId) >= MAX_RESUMES) {
            throw new BusinessRuleException("You can keep at most " + MAX_RESUMES + " resumes. Delete one first.");
        }
        UUID id = UUID.randomUUID();
        String key = profileId + "/" + id;
        storage.store(key, content);
        try {
            boolean first = repo.activeResumeCount(profileId) == 0;
            ResumeItem created = repo.insertResume(profileId, id, safeName(originalName, contentType), key, contentType,
                    content.length, sha256(content), first, clock.instant());
            profiles.recalculateCompleteness(profileId);
            return created;
        } catch (RuntimeException e) {
            storage.delete(key);
            throw e;
        }
    }

    @Transactional
    public void setPrimaryResume(UUID userId, UUID id) {
        UUID profileId = profile(userId);
        if (!repo.setPrimaryResume(profileId, id)) {
            throw new ResourceNotFoundException("Resume not found.");
        }
        profiles.recalculateCompleteness(profileId);
    }

    @Transactional
    public void deleteResume(UUID userId, UUID id) {
        UUID profileId = profile(userId);
        if (!repo.softDeleteResume(profileId, id, clock.instant())) {
            throw new ResourceNotFoundException("Resume not found.");
        }
        profiles.recalculateCompleteness(profileId);
    }

    public record Download(ResumeFile file, byte[] content) {}

    @Transactional(readOnly = true)
    public Download download(UUID userId, UUID id) {
        ResumeFile file = repo.findResume(profile(userId), id)
                .orElseThrow(() -> new ResourceNotFoundException("Resume not found."));
        return new Download(file, storage.load(file.storageKey()));
    }

    /** Magic-byte verification (API_CONTRACT §10): the client-declared content type is never trusted. */
    static String sniff(byte[] c) {
        if (c.length >= 5 && c[0] == '%' && c[1] == 'P' && c[2] == 'D' && c[3] == 'F' && c[4] == '-') {
            return PDF;
        }
        if (c.length >= 4 && c[0] == 'P' && c[1] == 'K' && c[2] == 3 && c[3] == 4 && containsWordDocument(c)) {
            return DOCX;
        }
        return null;
    }

    private static boolean containsWordDocument(byte[] c) {
        // A DOCX is a ZIP whose first entries name word/ parts; a plain ZIP is rejected.
        String head = new String(c, 0, Math.min(c.length, 4096), StandardCharsets.ISO_8859_1);
        return head.contains("[Content_Types].xml") || head.contains("word/");
    }

    static String safeName(String original, String contentType) {
        String name = original == null ? "" : original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}\"<>|:*?]", "").trim();
        String ext = PDF.equals(contentType) ? ".pdf" : ".docx";
        if (name.isEmpty()) {
            name = "resume" + ext;
        }
        if (!name.toLowerCase(Locale.ROOT).endsWith(ext)) {
            name = name + ext;
        }
        return name.length() <= 255 ? name : name.substring(name.length() - 255);
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
