package com.jobforge.aiservice.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobforge.aiservice.domain.AiOperation;
import com.jobforge.aiservice.domain.AiRequestStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Persistence boundary for AI requests and their idempotency records. */
@Repository
public class AiRequestStore {
    public record RequestRow(UUID id, UUID userId, String role, AiOperation operation,
            AiRequestStatus status, String inputHash, JsonNode output, String provider,
            String model, Integer creditsCharged, Instant createdAt) {}
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public AiRequestStore(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public Optional<RequestRow> findByIdempotencyKey(UUID userId, UUID key) {
        return jdbc.query("""
                SELECT id,user_id,user_role,operation,status,input_hash,output,provider,model,
                       credits_charged,created_at
                  FROM ai.ai_requests
                 WHERE user_id=? AND idempotency_key=?
                """, rs -> rs.next() ? Optional.of(map(rs)) : Optional.empty(), userId, key);
    }

    public void insertPending(UUID id, UUID userId, String role, AiOperation operation,
            JsonNode input, String inputHash, UUID idempotencyKey, String traceId,
            String provider, String model) {
        jdbc.update("""
                INSERT INTO ai.ai_requests
                    (id,user_id,user_role,operation,status,provider,model,input,input_hash,
                     idempotency_key,trace_id,created_at)
                VALUES (?,?,?,?,'PENDING',?,?,?,?,?,?,now())
                """, id, userId, role, operation.name(), provider, model,
                json(input), inputHash, idempotencyKey, traceId);
    }

    public void markSucceeded(UUID id, JsonNode output, String schemaVersion,
            JsonNode validationResult, Integer qualityScore, int inputTokens,
            int outputTokens, int credits) {
        jdbc.update("""
                UPDATE ai.ai_requests
                   SET status='SUCCEEDED', output=?::jsonb, output_schema_version=?,
                       validation_result=?::jsonb, quality_score=?, tokens_input=?,
                       tokens_output=?, credits_charged=?, completed_at=now()
                 WHERE id=? AND status='PENDING'
                """, json(output), schemaVersion, json(validationResult), qualityScore,
                inputTokens, outputTokens, credits, id);
    }

    public void markFailed(UUID id, String errorCode) {
        jdbc.update("""
                UPDATE ai.ai_requests
                   SET status='FAILED', error_code=?, completed_at=now()
                 WHERE id=? AND status='PENDING'
                """, errorCode, id);
    }

    public Optional<RequestRow> findOwned(UUID userId, UUID requestId) {
        return jdbc.query("""
                SELECT id,user_id,user_role,operation,status,input_hash,output,provider,model,
                       credits_charged,created_at
                  FROM ai.ai_requests WHERE user_id=? AND id=?
                """, rs -> rs.next() ? Optional.of(map(rs)) : Optional.empty(), userId, requestId);
    }

    private RequestRow map(java.sql.ResultSet rs) throws java.sql.SQLException {
        String output = rs.getString("output");
        JsonNode node = null;
        if (output != null) {
            try { node = mapper.readTree(output); }
            catch (java.io.IOException e) { throw new java.sql.SQLException("Stored AI output is invalid", e); }
        }
        return new RequestRow(rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class),
                rs.getString("user_role"), AiOperation.valueOf(rs.getString("operation")),
                AiRequestStatus.valueOf(rs.getString("status")), rs.getString("input_hash"),
                node, rs.getString("provider"), rs.getString("model"),
                (Integer) rs.getObject("credits_charged"), Db.instant(rs, "created_at"));
    }

    private String json(JsonNode node) {
        try { return node == null ? null : mapper.writeValueAsString(node); }
        catch (java.io.IOException e) { throw new IllegalArgumentException("Cannot serialize AI JSON", e); }
    }
}
