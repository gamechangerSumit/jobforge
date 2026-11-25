package com.jobforge.backend.ai.api;

import com.jobforge.backend.ai.app.AiGatewayService;
import com.jobforge.backend.ai.facade.AiContextTextSanitizer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jobforge.backend.company.facade.CompanyAccessFacade;
import com.jobforge.backend.profile.facade.ProfileFacade;
import com.jobforge.backend.profile.facade.SeekerDataFacade;
import com.jobforge.backend.search.facade.SearchFacade;
import java.util.LinkedHashMap;
import java.util.List;
import com.jobforge.backend.shared.api.CursorMeta;
import com.jobforge.backend.shared.api.CursorResponse;
import com.jobforge.backend.shared.error.ForbiddenException;
import com.jobforge.backend.shared.error.ValidationFailedException;
import com.jobforge.backend.shared.security.AuthenticatedUser;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public AI gateway. All identity and authorization data comes from the verified backend principal. */
@RestController
@RequestMapping("/ai")
public class AiController {
    private final AiGatewayService gateway;
    private final ProfileFacade profiles;
    private final CompanyAccessFacade companies;
    private final SeekerDataFacade seekerData;
    private final SearchFacade search;
    private final ObjectMapper mapper;

    public AiController(AiGatewayService gateway, ProfileFacade profiles, CompanyAccessFacade companies,
            SeekerDataFacade seekerData, SearchFacade search, ObjectMapper mapper) {
        this.gateway = gateway;
        this.profiles = profiles;
        this.companies = companies;
        this.seekerData = seekerData;
        this.search = search;
        this.mapper = mapper;
    }

    @PostMapping("/job-drafts")
    @PreAuthorize("hasRole('RECRUITER')")
    public ResponseEntity<?> createJobDraft(@AuthenticationPrincipal AuthenticatedUser caller,
            @RequestBody JsonNode request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        if (request == null || !request.isObject()) {
            throw ValidationFailedException.of("body", "INVALID_TYPE", "A JSON object is required.");
        }
        if (!profiles.isRecruiterApproved(caller.id())) {
            throw new ForbiddenException(com.jobforge.backend.shared.error.ErrorCode.RECRUITER_NOT_APPROVED,
                    "Your recruiter account must be approved before using AI job drafting.");
        }
        var membership = companies.membershipOf(caller.id())
                .orElseThrow(() -> new ForbiddenException("Join or create a company before drafting a job."));
        if (request.hasNonNull("companyId") && !membership.companyId().toString().equals(request.path("companyId").asText())) {
            throw new ForbiddenException("You can only draft jobs for your own company.");
        }
        ObjectNode trusted = ((ObjectNode) request).deepCopy();
        trusted.put("companyId", membership.companyId().toString());
        return ResponseEntity.status(HttpStatus.OK).body(payload(gateway.post("/internal/v1/ai/job-drafts", trusted,
                caller.id(), caller.role().name(), requestId, idempotencyKey)));
    }

    @PostMapping("/job-validations")
    @PreAuthorize("hasRole('RECRUITER')")
    public Object validateJob(@AuthenticationPrincipal AuthenticatedUser caller, @RequestBody JsonNode request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        if (!profiles.isRecruiterApproved(caller.id())) {
            throw new ForbiddenException(com.jobforge.backend.shared.error.ErrorCode.RECRUITER_NOT_APPROVED,
                    "Your recruiter account must be approved before using AI validation.");
        }
        return payload(gateway.post("/internal/v1/ai/job-validations", request, caller.id(), caller.role().name(), requestId, idempotencyKey));
    }

    @GetMapping("/usage/me")
    public Object usage(@AuthenticationPrincipal AuthenticatedUser caller,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        return payload(gateway.get("/internal/v1/ai/usage?userId=" + caller.id(), caller.id(), caller.role().name(), requestId));
    }

    @GetMapping("/requests")
    public CursorResponse<Map<String, Object>> history(@AuthenticationPrincipal AuthenticatedUser caller,
            @RequestParam(defaultValue = "20") int limit, @RequestParam(required = false) String cursor,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        if (limit < 1 || limit > 50) throw ValidationFailedException.of("limit", "MAX", "Limit must be between 1 and 50.");
        String path = "/internal/v1/ai/requests?limit=" + limit + (cursor == null || cursor.isBlank() ? "" : "&cursor=" + java.net.URLEncoder.encode(cursor, java.nio.charset.StandardCharsets.UTF_8));
        Map<?, ?> response = gateway.get(path, caller.id(), caller.role().name(), requestId);
        Object rawData = response.get("data");
        Object rawMeta = response.get("meta");
        Map<?, ?> meta = rawMeta instanceof Map<?, ?> value ? value : Map.of();
        Object rawCursor = meta.get("cursor");
        Map<?, ?> cursorMetaMap = rawCursor instanceof Map<?, ?> value ? value : Map.of();
        @SuppressWarnings("unchecked") java.util.List<Map<String, Object>> items = rawData instanceof java.util.List<?> list
                ? (java.util.List<Map<String, Object>>) list : java.util.List.of();
        String next = cursorMetaMap.get("next") instanceof String value ? value : null;
        boolean hasNext = Boolean.TRUE.equals(cursorMetaMap.get("hasNext"));
        int pageLimit = cursorMetaMap.get("limit") instanceof Number value ? value.intValue() : limit;
        return new CursorResponse<>(items, new CursorMeta(next, hasNext, pageLimit));
    }

    @GetMapping("/requests/{id}")
    public Object requestDetail(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        return payload(gateway.get("/internal/v1/ai/requests/" + id, caller.id(), caller.role().name(), requestId));
    }

    @GetMapping("/admin/quota-policies")
    @PreAuthorize("hasRole('ADMIN')")
    public Object quotaPolicies(@AuthenticationPrincipal AuthenticatedUser caller,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        return payload(gateway.get("/internal/v1/ai/admin/quota-policies", caller.id(), caller.role().name(), requestId));
    }

    @PutMapping("/admin/quota-policies")
    @PreAuthorize("hasRole('ADMIN')")
    public Object updateQuotaPolicy(@AuthenticationPrincipal AuthenticatedUser caller, @RequestBody JsonNode request,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        return payload(gateway.put("/internal/v1/ai/admin/quota-policies", request,
                caller.id(), caller.role().name(), requestId));
    }

    @PutMapping("/admin/quota-overrides/{targetUserId}")
    @PreAuthorize("hasRole('ADMIN')")
    public Object putQuotaOverride(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID targetUserId,
            @RequestBody JsonNode request, @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        return payload(gateway.put("/internal/v1/ai/admin/quota-overrides/" + targetUserId, request,
                caller.id(), caller.role().name(), requestId));
    }

    @DeleteMapping("/admin/quota-overrides/{targetUserId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteQuotaOverride(@AuthenticationPrincipal AuthenticatedUser caller,
            @PathVariable UUID targetUserId, @RequestParam(required = false) String period,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        String path = "/internal/v1/ai/admin/quota-overrides/" + targetUserId
                + (period == null || period.isBlank() ? "" : "?period="
                + java.net.URLEncoder.encode(period, java.nio.charset.StandardCharsets.UTF_8));
        gateway.delete(path, caller.id(), caller.role().name(), requestId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/admin/usage")
    @PreAuthorize("hasRole('ADMIN')")
    public Object adminUsage(@AuthenticationPrincipal AuthenticatedUser caller,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        return payload(gateway.get("/internal/v1/ai/admin/usage", caller.id(), caller.role().name(), requestId));
    }

    @PostMapping("/recommendations/jobs")
    @PreAuthorize("hasRole('JOB_SEEKER')")
    public Object recommendJobs(@AuthenticationPrincipal AuthenticatedUser caller,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        ObjectNode request = trustedSeekerContext(caller.id());
        return payload(gateway.post("/internal/v1/ai/recommendations/jobs", request,
                caller.id(), caller.role().name(), requestId, null));
    }

    @PostMapping("/career-insights")
    @PreAuthorize("hasRole('JOB_SEEKER')")
    public Object careerInsights(@AuthenticationPrincipal AuthenticatedUser caller,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        ObjectNode request = trustedSeekerContext(caller.id());
        return payload(gateway.post("/internal/v1/ai/career-insights", request,
                caller.id(), caller.role().name(), requestId, null));
    }


    /**
     * Build context only from the verified caller's own profile and public job search.
     * Contact details, salary expectations, resume contents, and application notes are never sent to the AI provider.
     */
    private ObjectNode trustedSeekerContext(UUID userId) {
        var profile = seekerData.candidate(userId).orElseThrow(() ->
                ValidationFailedException.of("profile", "NOT_FOUND", "Complete your seeker profile before using career AI."));
        List<String> skills = profile.skills() == null ? List.of()
                : profile.skills().stream().map(SeekerDataFacade.SkillView::skill).filter(java.util.Objects::nonNull)
                        .map(AiContextTextSanitizer::sanitize)
                        .map(String::trim).filter(value -> !value.isBlank()).distinct().limit(20).toList();
        String city = profile.location() == null ? null : profile.location().city();
        String country = profile.location() == null ? null : profile.location().country();
        List<SearchFacade.RecommendationJob> matches = search.recommendForSeeker(skills, city, country, 10);

        Map<String, Object> profileContext = new LinkedHashMap<>();
        putIfPresent(profileContext, "headline", profile.headline());
        putIfPresent(profileContext, "summary", profile.summary());
        putIfPresent(profileContext, "currentTitle", profile.currentTitle());
        profileContext.put("yearsExperience", profile.yearsExperience());
        profileContext.put("openToWork", profile.openToWork());
        profileContext.put("profileCompleteness", profile.completenessScore());
        profileContext.put("skills", skills);
        profileContext.put("skillDetails", profile.skills() == null ? List.of() : profile.skills().stream()
                .map(skill -> Map.of("name", AiContextTextSanitizer.sanitize(skill.skill()), "proficiency",
                        skill.proficiency() == null ? "UNSPECIFIED" : skill.proficiency(),
                        "years", skill.years() == null ? 0 : skill.years()))
                .toList());
        if (profile.location() != null) {
            Map<String, Object> location = new LinkedHashMap<>();
            putIfPresent(location, "city", profile.location().city());
            putIfPresent(location, "state", profile.location().state());
            putIfPresent(location, "country", profile.location().country());
            profileContext.put("location", location);
        }

        List<Map<String, Object>> jobs = matches.stream().map(this::aiJobContext).toList();
        ObjectNode request = mapper.createObjectNode();
        request.set("context", mapper.valueToTree(Map.of("profile", profileContext, "jobs", jobs)));
        return request;
    }

    private Map<String, Object> aiJobContext(SearchFacade.RecommendationJob job) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", job.id());
        value.put("title", AiContextTextSanitizer.sanitize(job.title()));
        value.put("skills", job.skills() == null ? List.of() : job.skills().stream().map(AiContextTextSanitizer::sanitize).toList());
        putIfPresent(value, "workMode", job.workMode());
        putIfPresent(value, "employmentType", job.employmentType());
        putIfPresent(value, "experienceLevel", job.experienceLevel());
        putIfPresent(value, "companyName", job.companyName());
        Map<String, Object> location = new LinkedHashMap<>();
        putIfPresent(location, "city", job.city());
        putIfPresent(location, "state", job.state());
        putIfPresent(location, "country", job.country());
        if (!location.isEmpty()) value.put("location", location);
        return value;
    }

    private static void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value instanceof String text) {
            String safe = AiContextTextSanitizer.sanitize(text);
            if (!safe.isBlank()) target.put(key, safe);
        } else if (value != null) target.put(key, value);
    }
    private static Object payload(Map<?, ?> envelope) {
        Object data = envelope == null ? null : envelope.get("data");
        return data == null ? Map.of() : data;
    }
}
