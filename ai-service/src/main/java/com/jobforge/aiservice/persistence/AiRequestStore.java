package com.jobforge.aiservice.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobforge.aiservice.domain.AiOperation;
import com.jobforge.aiservice.domain.AiRequestStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Persistence boundary for AI requests, idempotency records and caller-owned history. */
@Repository
public class AiRequestStore {
    public record RequestRow(UUID id, UUID userId, String role, AiOperation operation,
            AiRequestStatus status, String inputHash, JsonNode output, JsonNode validationResult,
            String schemaVersion, Integer qualityScore, String provider, String model,
            Integer creditsCharged, Instant createdAt) {}
    public record RequestPage(List<RequestRow> items, boolean hasNext) {}

    private static final String SELECT_COLUMNS = """
            id,user_id,user_role,operation,status,input_hash,output,validation_result,
            output_schema_version,quality_score,provider,model,credits_charged,created_at
            """;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public AiRequestStore(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public Optional<RequestRow> findByIdempotencyKey(UUID userId, UUID key) {
        return jdbc.query("SELECT " + SELECT_COLUMNS + " FROM ai.ai_requests WHERE user_id=? AND idempotency_key=?",
                rs -> rs.next() ? Optional.of(map(rs)) : Optional.empty(), userId, key);
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
        return jdbc.query("SELECT " + SELECT_COLUMNS + " FROM ai.ai_requests WHERE user_id=? AND id=?",
                rs -> rs.next() ? Optional.of(map(rs)) : Optional.empty(), userId, requestId);
    }

    public RequestPage pageOwned(UUID userId, AiCursorCodec.Cursor cursor, int limit) {
        List<RequestRow> rows;
        if(cursor==null) {
            rows=jdbc.query("SELECT "+SELECT_COLUMNS+" FROM ai.ai_requests WHERE user_id=? ORDER BY created_at DESC,id DESC LIMIT ?",
                    (rs,row)->map(rs),userId,limit+1);
        } else {
            rows=jdbc.query("""
                    SELECT %s FROM ai.ai_requests
                     WHERE user_id=? AND (created_at<? OR (created_at=? AND id<?))
                     ORDER BY created_at DESC,id DESC LIMIT ?
                    """.formatted(SELECT_COLUMNS),(rs,row)->map(rs),userId,
                    Db.ts(cursor.createdAt()),Db.ts(cursor.createdAt()),cursor.id(),limit+1);
        }
        boolean hasNext=rows.size()>limit;
        if(hasNext) rows=rows.subList(0,limit);
        return new RequestPage(List.copyOf(rows),hasNext);
    }

    private RequestRow map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new RequestRow(rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class),
                rs.getString("user_role"), AiOperation.valueOf(rs.getString("operation")),
                AiRequestStatus.valueOf(rs.getString("status")), rs.getString("input_hash"),
                parse(rs.getString("output")), parse(rs.getString("validation_result")),
                rs.getString("output_schema_version"), (Integer) rs.getObject("quality_score"),
                rs.getString("provider"), rs.getString("model"),
                (Integer) rs.getObject("credits_charged"), Db.instant(rs, "created_at"));
    }

    private JsonNode parse(String value) throws java.sql.SQLException {
        if (value == null) return null;
        try { return mapper.readTree(value); }
        catch (java.io.IOException e) { throw new java.sql.SQLException("Stored AI JSON is invalid", e); }
    }

    private String json(JsonNode node) {
        try { return node == null ? null : mapper.writeValueAsString(node); }
        catch (java.io.IOException e) { throw new IllegalArgumentException("Cannot serialize AI JSON", e); }
    }
}
