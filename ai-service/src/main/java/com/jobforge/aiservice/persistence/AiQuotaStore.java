package com.jobforge.aiservice.persistence;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Transactional quota reservation backed by the PostgreSQL usage ledger. */
@Repository
public class AiQuotaStore {
    public record QuotaSnapshot(int limit, int used, int remaining, Instant resetsAt, int rateLimitPerMinute) {}
    private final JdbcTemplate jdbc;

    public AiQuotaStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public QuotaSnapshot reserve(UUID userId, String role, UUID requestId, String operation, int credits) {
        var policies = jdbc.query("""
                SELECT credit_limit,rate_limit_per_minute
                  FROM ai.ai_quota_policies
                 WHERE role=? AND period='DAILY' AND active=true
                 FOR UPDATE
                """, (rs, row) -> new int[]{rs.getInt(1), rs.getInt(2)}, role);
        if (policies.isEmpty()) throw new IllegalStateException("No active daily AI quota policy for role");
        int limit = policies.get(0)[0];
        int rate = policies.get(0)[1];
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Integer usedValue = jdbc.queryForObject("""
                SELECT COALESCE(sum(credits),0)::int
                  FROM ai.ai_usage_ledger
                 WHERE user_id=? AND period_day=? AND state IN ('RESERVED','COMMITTED')
                """, Integer.class, userId, today);
        int used = usedValue == null ? 0 : usedValue;
        if (used + credits > limit) {
            Instant reset = today.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
            long seconds = Math.max(1, reset.getEpochSecond() - Instant.now().getEpochSecond());
            throw com.jobforge.aiservice.error.AiServiceException.quotaExceeded(reset, seconds);
        }
        jdbc.update("""
                INSERT INTO ai.ai_usage_ledger
                    (user_id,request_id,operation,credits,state,period_day,created_at)
                VALUES (?,?,?,?,'RESERVED',?,now())
                """, userId, requestId, operation, credits, today);
        return new QuotaSnapshot(limit, used + credits, limit - used - credits,
                today.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC), rate);
    }

    public void commit(UUID requestId) {
        jdbc.update("UPDATE ai.ai_usage_ledger SET state='COMMITTED',settled_at=now() WHERE request_id=? AND state='RESERVED'", requestId);
    }

    public void release(UUID requestId) {
        jdbc.update("UPDATE ai.ai_usage_ledger SET state='RELEASED',settled_at=now() WHERE request_id=? AND state='RESERVED'", requestId);
    }

    public QuotaSnapshot snapshot(UUID userId, String role) {
        var rows = jdbc.query("""
                SELECT credit_limit,rate_limit_per_minute FROM ai.ai_quota_policies
                 WHERE role=? AND period='DAILY' AND active=true
                """, (rs, row) -> new int[]{rs.getInt(1), rs.getInt(2)}, role);
        if (rows.isEmpty()) throw new IllegalStateException("No active daily AI quota policy for role");
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Integer usedValue = jdbc.queryForObject("""
                SELECT COALESCE(sum(credits),0)::int FROM ai.ai_usage_ledger
                 WHERE user_id=? AND period_day=? AND state='COMMITTED'
                """, Integer.class, userId, today);
        int used = usedValue == null ? 0 : usedValue;
        int limit = rows.get(0)[0];
        return new QuotaSnapshot(limit, used, Math.max(0, limit - used),
                today.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC), rows.get(0)[1]);
    }
}
