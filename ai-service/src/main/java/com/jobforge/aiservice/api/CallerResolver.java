package com.jobforge.aiservice.api;

import com.jobforge.aiservice.domain.CallerIdentity;
import com.jobforge.aiservice.error.AiServiceException;
import java.util.UUID;

/** Validates trusted identity headers forwarded by the backend. */
final class CallerResolver {
    private CallerResolver() {}
    static CallerIdentity resolve(String userId, String role) {
        UUID id;
        try {
            id = UUID.fromString(userId == null ? "" : userId.trim());
        } catch (IllegalArgumentException e) {
            throw AiServiceException.validation("X-User-Id", "PATTERN", "must be a UUID");
        }
        String normalized = role == null ? "" : role.trim();
        if (!CallerIdentity.ROLES.contains(normalized)) {
            throw AiServiceException.validation("X-User-Role", "INVALID_ENUM", "must be one of the allowed values");
        }
        return new CallerIdentity(id, normalized);
    }
    static UUID optionalUuid(String header, String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            throw AiServiceException.validation(header, "PATTERN", "must be a UUID");
        }
    }
}
