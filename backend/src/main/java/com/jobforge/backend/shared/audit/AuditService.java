package com.jobforge.backend.shared.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobforge.backend.shared.persistence.Db;
import com.jobforge.backend.shared.web.ClientInfo;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes {@code core.audit_logs} in the CALLER's transaction (DATABASE_SCHEMA §13, ARCHITECTURE §18).
 * Request metadata comes from the current request. Secret-like keys are redacted.
 */
@Service
public class AuditService {

    private static final List<String> SECRET_MARKERS = List.of("password", "token", "secret", "hash", "authorization", "cookie");

    private final JdbcClient jdbc;
    private final ObjectMapper mapper;
    private final Clock clock;

    public AuditService(JdbcClient jdbc, ObjectMapper mapper, Clock clock) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(AuditEntry entry) {
        ClientInfo client = ClientInfo.current();
        jdbc.sql("""
                INSERT INTO core.audit_logs
                  (id, occurred_at, actor_user_id, actor_role, source, action, entity_type, entity_id, outcome,
                   before_state, after_state, metadata, ip, user_agent, request_id)
                VALUES
                  (:id, :at, :actor, :role, 'BACKEND', :action, :etype, :eid, :outcome,
                   CAST(:before AS jsonb), CAST(:after AS jsonb), CAST(:meta AS jsonb),
                   CAST(:ip AS inet), :ua, CAST(:rid AS uuid))
                """)
                .param("id", UUID.randomUUID())
                .param("at", Db.ts(clock.instant()))
                .param("actor", entry.actorUserId())
                .param("role", entry.actorRole() == null ? null : entry.actorRole().name())
                .param("action", entry.action())
                .param("etype", entry.entityType())
                .param("eid", entry.entityId())
                .param("outcome", entry.outcome().name())
                .param("before", json(entry.before()))
                .param("after", json(entry.after()))
                .param("meta", json(entry.metadata()))
                .param("ip", client.ip())
                .param("ua", client.userAgent())
                .param("rid", client.requestId())
                .update();
    }

    private String json(Object value) {
        if (value == null) {
            return null;
        }
        try {
            Object tree = mapper.convertValue(value, new TypeReference<Object>() {});
            return mapper.writeValueAsString(redact(tree));
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new IllegalStateException("Audit payload could not be serialized", e);
        }
    }

    @SuppressWarnings("unchecked")
    static Object redact(Object node) {
        if (node instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            ((Map<String, Object>) map).forEach((k, v) -> out.put(k, isSecretKey(k) ? "[REDACTED]" : redact(v)));
            return out;
        }
        if (node instanceof List<?> list) {
            return list.stream().map(AuditService::redact).toList();
        }
        return node;
    }

    private static boolean isSecretKey(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        return SECRET_MARKERS.stream().anyMatch(lower::contains);
    }
}
