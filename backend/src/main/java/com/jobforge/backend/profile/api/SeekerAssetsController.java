package com.jobforge.backend.profile.api;

import com.jobforge.backend.profile.api.SeekerAssetsRequests.EducationCreate;
import com.jobforge.backend.profile.api.SeekerAssetsRequests.EducationPatch;
import com.jobforge.backend.profile.api.SeekerAssetsRequests.ExperienceCreate;
import com.jobforge.backend.profile.api.SeekerAssetsRequests.ExperiencePatch;
import com.jobforge.backend.profile.api.SeekerAssetsRequests.Skills;
import com.jobforge.backend.profile.app.SeekerAssets.EducationInput;
import com.jobforge.backend.profile.app.SeekerAssets.EducationItem;
import com.jobforge.backend.profile.app.SeekerAssets.ExperienceInput;
import com.jobforge.backend.profile.app.SeekerAssets.ExperienceItem;
import com.jobforge.backend.profile.app.SeekerAssets.ResumeItem;
import com.jobforge.backend.profile.app.SeekerAssets.SkillItem;
import com.jobforge.backend.profile.app.SeekerAssetsService;
import com.jobforge.backend.shared.error.ApiException;
import com.jobforge.backend.shared.error.ErrorCode;
import com.jobforge.backend.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** API_CONTRACT §12.2 — skills, education, experience and resumes under {@code /seekers/me} (JOB_SEEKER only). */
@RestController
@RequestMapping("/seekers/me")
@PreAuthorize("hasRole('JOB_SEEKER')")
public class SeekerAssetsController {

    private final SeekerAssetsService assets;

    public SeekerAssetsController(SeekerAssetsService assets) {
        this.assets = assets;
    }

    // ------------------------------------------------------------------ skills

    @GetMapping("/skills")
    public List<SkillItem> skills(@AuthenticationPrincipal AuthenticatedUser caller) {
        return assets.skills(caller.id());
    }

    @PutMapping("/skills")
    public List<SkillItem> replaceSkills(@AuthenticationPrincipal AuthenticatedUser caller,
            @Valid @RequestBody Skills request) {
        return assets.replaceSkills(caller.id(), request.skills().stream()
                .map(s -> new SkillItem(s.skill(), s.proficiency(), s.years())).toList());
    }

    // --------------------------------------------------------------- education

    @GetMapping("/education")
    public List<EducationItem> education(@AuthenticationPrincipal AuthenticatedUser caller) {
        return assets.education(caller.id());
    }

    @PostMapping("/education")
    public ResponseEntity<EducationItem> addEducation(@AuthenticationPrincipal AuthenticatedUser caller,
            @Valid @RequestBody EducationCreate r) {
        EducationItem created = assets.addEducation(caller.id(), new EducationInput(r.institution(), r.degree(),
                r.fieldOfStudy(), r.startDate(), r.endDate(), r.grade(), r.description()));
        return ResponseEntity.created(URI.create("/api/v1/seekers/me/education/" + created.id())).body(created);
    }

    @PatchMapping("/education/{id}")
    public EducationItem patchEducation(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id,
            @Valid @RequestBody EducationPatch r) {
        return assets.patchEducation(caller.id(), id, new EducationInput(r.institution(), r.degree(),
                r.fieldOfStudy(), r.startDate(), r.endDate(), r.grade(), r.description()), Boolean.TRUE.equals(r.clearEndDate()));
    }

    @DeleteMapping("/education/{id}")
    public ResponseEntity<Void> deleteEducation(@AuthenticationPrincipal AuthenticatedUser caller,
            @PathVariable UUID id) {
        assets.deleteEducation(caller.id(), id);
        return ResponseEntity.noContent().build();
    }

    // -------------------------------------------------------------- experience

    @GetMapping("/experience")
    public List<ExperienceItem> experience(@AuthenticationPrincipal AuthenticatedUser caller) {
        return assets.experience(caller.id());
    }

    @PostMapping("/experience")
    public ResponseEntity<ExperienceItem> addExperience(@AuthenticationPrincipal AuthenticatedUser caller,
            @Valid @RequestBody ExperienceCreate r) {
        ExperienceItem created = assets.addExperience(caller.id(), new ExperienceInput(r.title(), r.companyName(),
                r.location(), r.employmentType(), r.startDate(), r.endDate(), Boolean.TRUE.equals(r.current()),
                r.description()));
        return ResponseEntity.created(URI.create("/api/v1/seekers/me/experience/" + created.id())).body(created);
    }

    @PatchMapping("/experience/{id}")
    public ExperienceItem patchExperience(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id,
            @Valid @RequestBody ExperiencePatch r) {
        return assets.patchExperience(caller.id(), id, new ExperienceInput(r.title(), r.companyName(), r.location(),
                r.employmentType(), r.startDate(), r.endDate(), false, r.description()), r.current(),
                Boolean.TRUE.equals(r.clearEndDate()));
    }

    @DeleteMapping("/experience/{id}")
    public ResponseEntity<Void> deleteExperience(@AuthenticationPrincipal AuthenticatedUser caller,
            @PathVariable UUID id) {
        assets.deleteExperience(caller.id(), id);
        return ResponseEntity.noContent().build();
    }

    // ----------------------------------------------------------------- resumes

    @GetMapping("/resumes")
    public List<ResumeItem> resumes(@AuthenticationPrincipal AuthenticatedUser caller) {
        return assets.resumes(caller.id());
    }

    @PostMapping(value = "/resumes", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResumeItem> uploadResume(@AuthenticationPrincipal AuthenticatedUser caller,
            @RequestPart("file") MultipartFile file) {
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ApiException(ErrorCode.MALFORMED_REQUEST, "The uploaded file could not be read.");
        }
        ResumeItem created = assets.uploadResume(caller.id(), file.getOriginalFilename(), bytes);
        return ResponseEntity.created(URI.create("/api/v1/seekers/me/resumes/" + created.id())).body(created);
    }

    @PutMapping("/resumes/{id}/primary")
    public ResponseEntity<Void> setPrimary(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id) {
        assets.setPrimaryResume(caller.id(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/resumes/{id}/download")
    public ResponseEntity<byte[]> download(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id) {
        SeekerAssetsService.Download d = assets.download(caller.id(), id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(d.file().contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(d.file().originalFilename(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(d.content());
    }

    @DeleteMapping("/resumes/{id}")
    public ResponseEntity<Void> deleteResume(@AuthenticationPrincipal AuthenticatedUser caller,
            @PathVariable UUID id) {
        assets.deleteResume(caller.id(), id);
        return ResponseEntity.noContent().build();
    }
}
