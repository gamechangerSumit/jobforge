package com.jobforge.backend.shared.audit;

import com.jobforge.backend.shared.domain.UserRole;
import java.util.Map;
import java.util.UUID;

/** One audit record. {@code before}/{@code after}/{@code metadata} are redacted by {@link AuditService}. */
public record AuditEntry(
        String action,
        String entityType,
        UUID entityId,
        UUID actorUserId,
        UserRole actorRole,
        AuditOutcome outcome,
        Object before,
        Object after,
        Map<String, Object> metadata) {

    public static AuditEntry success(String action, String entityType, UUID entityId, UUID actorUserId, UserRole actorRole) {
        return new AuditEntry(action, entityType, entityId, actorUserId, actorRole, AuditOutcome.SUCCESS, null, null, null);
    }

    public static AuditEntry failure(String action, String entityType, UUID entityId) {
        return new AuditEntry(action, entityType, entityId, null, null, AuditOutcome.FAILURE, null, null, null);
    }

    public AuditEntry withMetadata(Map<String, Object> metadata) {
        return new AuditEntry(action, entityType, entityId, actorUserId, actorRole, outcome, before, after, metadata);
    }
}
