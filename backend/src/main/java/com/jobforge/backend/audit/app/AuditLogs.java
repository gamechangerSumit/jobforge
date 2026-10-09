package com.jobforge.backend.audit.app;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Read models and port for the admin audit viewer (API_CONTRACT 12.14, DATABASE_SCHEMA 4 audit_logs). */
public final class AuditLogs {

    private AuditLogs() {}

    public record Filter(UUID actorId, String action, String entityType, UUID entityId, Instant from, Instant to) {}

    public record Row(UUID id, Instant occurredAt, UUID actorUserId, String actorRole, String source, String action,
            String entityType, UUID entityId, String outcome, String beforeState, String afterState, String metadata,
            String ip, String userAgent, UUID requestId) {}

    public interface Repository {
        List<Row> search(Filter filter, int limit, int offset);

        long count(Filter filter);
    }
}
