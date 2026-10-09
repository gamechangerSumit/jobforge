package com.jobforge.backend.audit.api;

import com.fasterxml.jackson.annotation.JsonRawValue;
import com.jobforge.backend.audit.app.AuditLogQueryService;
import com.jobforge.backend.audit.app.AuditLogs.Filter;
import com.jobforge.backend.audit.app.AuditLogs.Row;
import com.jobforge.backend.shared.api.PagedResponse;
import com.jobforge.backend.shared.error.ValidationFailedException;
import com.jobforge.backend.shared.web.QueryParams;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API_CONTRACT 12.14 - GET /admin/audit-logs (ADMIN only, read-only). */
@RestController
@RequestMapping("/admin/audit-logs")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAuditLogController {

    public record AuditLogResponse(UUID id, Instant occurredAt, UUID actorUserId, String actorRole, String source,
            String action, String entityType, UUID entityId, String outcome,
            @JsonRawValue String beforeState, @JsonRawValue String afterState, @JsonRawValue String metadata,
            String ip, String userAgent, UUID requestId) {

        static AuditLogResponse from(Row r) {
            return new AuditLogResponse(r.id(), r.occurredAt(), r.actorUserId(), r.actorRole(), r.source(), r.action(),
                    r.entityType(), r.entityId(), r.outcome(), r.beforeState(), r.afterState(), r.metadata(), r.ip(),
                    r.userAgent(), r.requestId());
        }
    }

    private final AuditLogQueryService service;

    public AdminAuditLogController(AuditLogQueryService service) {
        this.service = service;
    }

    @GetMapping
    public PagedResponse<AuditLogResponse> list(HttpServletRequest request) {
        QueryParams p = new QueryParams(request, "actorId", "action", "entityType", "entityId", "from", "to", "page", "size");
        Filter filter = new Filter(p.uuid("actorId"), p.string("action"), p.string("entityType"), p.uuid("entityId"),
                instant(p, "from"), instant(p, "to"));
        int page = p.page();
        int size = p.size();
        return service.search(filter, page, size).toResponse(AuditLogResponse::from, page, size);
    }

    private static Instant instant(QueryParams p, String name) {
        String value = p.string(name);
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            throw ValidationFailedException.of(name, "INVALID_FORMAT", "must be an ISO-8601 UTC timestamp");
        }
    }
}
