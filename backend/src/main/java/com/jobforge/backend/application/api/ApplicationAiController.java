package com.jobforge.backend.application.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jobforge.backend.ai.facade.AiFacade;
import com.jobforge.backend.ai.facade.AiContextTextSanitizer;
import com.jobforge.backend.application.facade.ApplicationFacade;
import com.jobforge.backend.company.facade.CompanyAccessFacade;
import com.jobforge.backend.job.facade.JobFacade;
import com.jobforge.backend.job.facade.JobFacade.Viewer;
import com.jobforge.backend.profile.facade.ProfileFacade;
import com.jobforge.backend.profile.facade.SeekerDataFacade;
import com.jobforge.backend.shared.error.ForbiddenException;
import com.jobforge.backend.shared.error.ResourceNotFoundException;
import com.jobforge.backend.shared.security.AuthenticatedUser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Recruiter AI candidate matching; context is assembled only after application/job ownership checks. */
@RestController
@RequestMapping("/recruiters/me/jobs")
public class ApplicationAiController {

    private final AiFacade ai;
    private final ApplicationFacade applications;
    private final JobFacade jobs;
    private final CompanyAccessFacade companies;
    private final ProfileFacade profiles;
    private final SeekerDataFacade seekers;
    private final ObjectMapper mapper;

    public ApplicationAiController(AiFacade ai, ApplicationFacade applications, JobFacade jobs,
            CompanyAccessFacade companies, ProfileFacade profiles, SeekerDataFacade seekers, ObjectMapper mapper) {
        this.ai = ai;
        this.applications = applications;
        this.jobs = jobs;
        this.companies = companies;
        this.profiles = profiles;
        this.seekers = seekers;
        this.mapper = mapper;
    }

    @PostMapping("/{jobId}/candidate-recommendations")
    @PreAuthorize("hasRole('RECRUITER')")
    public Object recommendCandidates(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID jobId,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        if (!profiles.isRecruiterApproved(caller.id())) {
            throw new ForbiddenException(com.jobforge.backend.shared.error.ErrorCode.RECRUITER_NOT_APPROVED,
                    "Your recruiter account must be approved before using AI candidate recommendations.");
        }
        var membership = companies.membershipOf(caller.id())
                .orElseThrow(() -> new ForbiddenException("Join or create a company before using AI recommendations."));
        if (!companies.isVerified(membership.companyId())) {
            throw new ForbiddenException(com.jobforge.backend.shared.error.ErrorCode.COMPANY_NOT_VERIFIED,
                    "Your company must be verified before using AI candidate recommendations.");
        }
        var job = jobs.getForApply(jobId).orElseThrow(() ->
                new ResourceNotFoundException("Job not found or not accepting applications."));
        if (!membership.companyId().equals(job.companyId())) {
            throw new ForbiddenException("You can only review candidates for your own company's jobs.");
        }
        var jobSummary = jobs.getSummaries(List.of(jobId), Viewer.ANONYMOUS).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Published job not found."));

        Map<String, UUID> applicationByRef = new LinkedHashMap<>();
        List<Map<String, Object>> candidateContext = new ArrayList<>();
        for (var context : applications.contexts(applications.applicationIdsOfJob(jobId)).values()) {
            if (!jobId.equals(context.jobId()) || context.closed()) continue;
            var candidate = seekers.candidate(context.seekerUserId()).orElse(null);
            if (candidate == null || "PRIVATE".equals(candidate.visibility())) continue;
            String candidateRef = "candidate-" + (candidateContext.size() + 1);
            candidateContext.add(candidateContext(candidate, candidateRef));
            applicationByRef.put(candidateRef, context.applicationId());
        }

        Map<String, Object> jobContext = new LinkedHashMap<>();
        jobContext.put("title", AiContextTextSanitizer.sanitize(jobSummary.title()));
        jobContext.put("skills", jobSummary.skills() == null ? List.of()
                : jobSummary.skills().stream().map(AiContextTextSanitizer::sanitize).toList());
        putIfPresent(jobContext, "workMode", jobSummary.workMode());
        putIfPresent(jobContext, "employmentType", jobSummary.employmentType());
        putIfPresent(jobContext, "experienceLevel", jobSummary.experienceLevel());

        ObjectNode trusted = mapper.createObjectNode();
        trusted.set("context", mapper.valueToTree(Map.of("job", jobContext, "candidates", candidateContext)));
        Map<?, ?> envelope = ai.recommendCandidates(trusted, caller.id(), caller.role().name(), requestId);
        Object rawData = envelope == null ? null : envelope.get("data");
        if (!(rawData instanceof Map<?, ?> source)) return Map.of();
        Map<String, Object> data = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (key instanceof String name) data.put(name, value);
        });
        Object rawCandidates = data.get("candidates");
        if (rawCandidates instanceof List<?> ranked) {
            List<Map<String, Object>> linked = new ArrayList<>();
            for (Object item : ranked) {
                if (!(item instanceof Map<?, ?> candidate)) continue;
                UUID applicationId = applicationByRef.get(String.valueOf(candidate.get("candidateRef")));
                if (applicationId == null) continue;
                Map<String, Object> result = new LinkedHashMap<>();
                candidate.forEach((key, value) -> {
                    if (key instanceof String name && !"candidateRef".equals(name)) result.put(name, value);
                });
                result.put("applicationId", applicationId.toString());
                linked.add(result);
            }
            data.put("candidates", linked);
        }
        return data;
    }

    /** Candidate context is limited to recruiter-visible professional fields; contact and salary data are excluded. */
    private static Map<String, Object> candidateContext(SeekerDataFacade.CandidateProfile candidate, String candidateRef) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("candidateRef", candidateRef);
        putIfPresent(value, "headline", candidate.headline());
        putIfPresent(value, "summary", candidate.summary());
        putIfPresent(value, "currentTitle", candidate.currentTitle());
        value.put("yearsExperience", candidate.yearsExperience());
        value.put("profileCompleteness", candidate.completenessScore());
        value.put("skills", candidate.skills() == null ? List.of()
                : candidate.skills().stream().map(SeekerDataFacade.SkillView::skill)
                        .map(AiContextTextSanitizer::sanitize).toList());
        return value;
    }

    private static void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value instanceof String text) {
            String safe = AiContextTextSanitizer.sanitize(text);
            if (!safe.isBlank()) target.put(key, safe);
        } else if (value != null) target.put(key, value);
    }
}
