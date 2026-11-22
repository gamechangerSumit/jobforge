package com.jobforge.backend.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jobforge.backend.company.facade.CompanyAccessFacade;
import com.jobforge.backend.profile.facade.ProfileFacade;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    public AiController(AiGatewayService gateway, ProfileFacade profiles, CompanyAccessFacade companies) {
        this.gateway = gateway;
        this.profiles = profiles;
        this.companies = companies;
    }

    @PostMapping("/job-drafts")
    @PreAuthorize("hasRole('RECRUITER')")
    public ResponseEntity<Map<?, ?>> createJobDraft(@AuthenticationPrincipal AuthenticatedUser caller,
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
    public Map<?, ?> validateJob(@AuthenticationPrincipal AuthenticatedUser caller, @RequestBody JsonNode request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        if (!profiles.isRecruiterApproved(caller.id())) {
            throw new ForbiddenException(com.jobforge.backend.shared.error.ErrorCode.RECRUITER_NOT_APPROVED,
                    "Your recruiter account must be approved before using AI validation.");
        }
        return payload(gateway.post("/internal/v1/ai/job-validations", request, caller.id(), caller.role().name(), requestId, idempotencyKey));
    }

    @GetMapping("/usage/me")
    public Map<?, ?> usage(@AuthenticationPrincipal AuthenticatedUser caller,
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
    public Map<?, ?> requestDetail(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        return payload(gateway.get("/internal/v1/ai/requests/" + id, caller.id(), caller.role().name(), requestId));
    }

    @GetMapping("/admin/quota-policies")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<?, ?> quotaPolicies(@AuthenticationPrincipal AuthenticatedUser caller,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        return payload(gateway.get("/internal/v1/ai/admin/quota-policies", caller.id(), caller.role().name(), requestId));
    }

    @GetMapping("/admin/usage")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<?, ?> adminUsage(@AuthenticationPrincipal AuthenticatedUser caller,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        return payload(gateway.get("/internal/v1/ai/admin/usage", caller.id(), caller.role().name(), requestId));
    }
    private static Map<?, ?> payload(Map<?, ?> envelope) {
        Object data = envelope == null ? null : envelope.get("data");
        return data instanceof Map<?, ?> value ? value : Map.of();
    }
}
