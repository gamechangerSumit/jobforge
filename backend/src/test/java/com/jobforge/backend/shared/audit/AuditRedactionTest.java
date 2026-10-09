package com.jobforge.backend.shared.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuditRedactionTest {

    @Test
    @SuppressWarnings("unchecked")
    void secretLikeKeysAreRedactedRecursively() {
        Object out = AuditService.redact(Map.of(
                "passwordHash", "x", "refreshToken", "y", "status", "ACTIVE",
                "nested", Map.of("clientSecret", "z", "name", "ok"),
                "list", List.of(Map.of("Authorization", "Bearer t"))));
        Map<String, Object> map = (Map<String, Object>) out;
        assertThat(map).containsEntry("passwordHash", "[REDACTED]").containsEntry("refreshToken", "[REDACTED]")
                .containsEntry("status", "ACTIVE");
        assertThat((Map<String, Object>) map.get("nested")).containsEntry("clientSecret", "[REDACTED]").containsEntry("name", "ok");
        assertThat(((List<Map<String, Object>>) map.get("list")).get(0)).containsEntry("Authorization", "[REDACTED]");
    }
}
