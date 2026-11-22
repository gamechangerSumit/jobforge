package com.jobforge.aiservice.persistence;

import com.jobforge.aiservice.error.AiServiceException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Daily and monthly quota reservation backed by the PostgreSQL usage ledger. */
@Repository
public class AiQuotaStore {
    public record QuotaSnapshot(int limit, int used, int remaining, Instant resetsAt,
            int rateLimitPerMinute, String period) {}
    private record Policy(int limit,int rate) {}
    public record QuotaPolicy(String role,String period,int creditLimit,int rateLimitPerMinute,boolean active) {}
    private final JdbcTemplate jdbc;

    public AiQuotaStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public QuotaSnapshot reserve(UUID userId, String role, UUID requestId, String operation, int credits) {
        Map<String,Policy> policies=lockPolicies(role,userId);
        Policy daily=required(policies,"DAILY");
        Policy monthly=required(policies,"MONTHLY");
        LocalDate today=LocalDate.now(ZoneOffset.UTC);
        LocalDate monthStart=today.withDayOfMonth(1);
        int dailyUsed=used(userId,today);
        int monthlyUsed=used(userId,monthStart);
        int dailyRemaining=Math.max(0,daily.limit()-dailyUsed);
        int monthlyRemaining=Math.max(0,monthly.limit()-monthlyUsed);

        if(dailyRemaining<credits) {
            Instant reset=today.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
            throw AiServiceException.quotaExceeded(reset,reset.getEpochSecond()-Instant.now().getEpochSecond());
        }
        if(monthlyRemaining<credits) {
            Instant reset=YearMonth.from(today).plusMonths(1).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
            throw AiServiceException.quotaExceeded(reset,reset.getEpochSecond()-Instant.now().getEpochSecond());
        }

        String bindingPeriod=dailyRemaining<=monthlyRemaining?"DAILY":"MONTHLY";
        jdbc.update("""
                INSERT INTO ai.ai_usage_ledger
                    (user_id,request_id,operation,credits,state,period_day,created_at)
                VALUES (?,?,?,?,'RESERVED',?,now())
                """, userId, requestId, operation, credits, today);
        if("DAILY".equals(bindingPeriod)) {
            return new QuotaSnapshot(daily.limit(),dailyUsed+credits,dailyRemaining-credits,
                    today.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC),daily.rate(),"DAILY");
        }
        Instant reset=YearMonth.from(today).plusMonths(1).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        return new QuotaSnapshot(monthly.limit(),monthlyUsed+credits,monthlyRemaining-credits,
                reset,monthly.rate(),"MONTHLY");
    }

    public void commit(UUID requestId) {
        jdbc.update("UPDATE ai.ai_usage_ledger SET state='COMMITTED',settled_at=now() WHERE request_id=? AND state='RESERVED'", requestId);
    }

    public void release(UUID requestId) {
        jdbc.update("UPDATE ai.ai_usage_ledger SET state='RELEASED',settled_at=now() WHERE request_id=? AND state='RESERVED'", requestId);
    }

    public int releaseTimedOutReservations() {
        return jdbc.update("""
                UPDATE ai.ai_usage_ledger
                   SET state='RELEASED',settled_at=now()
                 WHERE state='RESERVED' AND request_id IN (
                       SELECT id FROM ai.ai_requests
                        WHERE status='FAILED' AND error_code='AI_TIMEOUT'
                          AND completed_at > now() - interval '1 day')
                """);
    }

    public java.util.List<QuotaPolicy> listPolicies() {
        return jdbc.query("""
                SELECT role,period,credit_limit,rate_limit_per_minute,active
                  FROM ai.ai_quota_policies ORDER BY role,period
                """,(rs,row)->new QuotaPolicy(rs.getString("role"),rs.getString("period"),
                rs.getInt("credit_limit"),rs.getInt("rate_limit_per_minute"),rs.getBoolean("active")));
    }

    public void upsertPolicy(String role,String period,int creditLimit,int rateLimit,boolean active,UUID adminId) {
        jdbc.update("""
                INSERT INTO ai.ai_quota_policies(role,period,credit_limit,rate_limit_per_minute,active,updated_by,updated_at)
                VALUES (?,?,?,?,?,?,now())
                ON CONFLICT(role,period) DO UPDATE SET credit_limit=excluded.credit_limit,
                    rate_limit_per_minute=excluded.rate_limit_per_minute,active=excluded.active,
                    updated_by=excluded.updated_by,updated_at=now()
                """,role,period,creditLimit,rateLimit,active,adminId);
    }

    public void putOverride(UUID userId,String period,int creditLimit,java.time.OffsetDateTime expiresAt,
            String reason,UUID adminId) {
        jdbc.update("""
                INSERT INTO ai.ai_quota_overrides(user_id,period,credit_limit,expires_at,reason,created_by,created_at)
                VALUES (?,?,?,?,?,?,now())
                ON CONFLICT(user_id,period) DO UPDATE SET credit_limit=excluded.credit_limit,
                    expires_at=excluded.expires_at,reason=excluded.reason,
                    created_by=excluded.created_by,created_at=now()
                """,userId,period,creditLimit,expiresAt,reason,adminId);
    }

    public int deleteOverride(UUID userId,String period) {
        return jdbc.update("DELETE FROM ai.ai_quota_overrides WHERE user_id=? AND period=?",userId,period);
    }

    public java.util.List<Map<String,Object>> adminUsage() {
        return jdbc.query("""
                SELECT operation,count(*)::int AS request_count,
                       COALESCE(sum(credits),0)::int AS credits_used,
                       count(*) FILTER (WHERE state='RELEASED')::int AS released_count
                  FROM ai.ai_usage_ledger
                 WHERE period_day>=current_date-29
                 GROUP BY operation ORDER BY operation
                """,(rs,row)->Map.<String,Object>of("operation",rs.getString("operation"),
                "requestCount",rs.getInt("request_count"),"creditsUsed",rs.getInt("credits_used"),
                "releasedCount",rs.getInt("released_count")));
    }

    public QuotaSnapshot snapshot(UUID userId, String role) {
        Map<String,Policy> policies=lockPolicies(role,userId);
        Policy daily=required(policies,"DAILY");
        Policy monthly=required(policies,"MONTHLY");
        LocalDate today=LocalDate.now(ZoneOffset.UTC);
        int dailyUsed=usedCommitted(userId,today);
        int monthlyUsed=usedCommitted(userId,today.withDayOfMonth(1));
        int dailyRemaining=Math.max(0,daily.limit()-dailyUsed);
        int monthlyRemaining=Math.max(0,monthly.limit()-monthlyUsed);
        if(dailyRemaining<=monthlyRemaining) {
            return new QuotaSnapshot(daily.limit(),dailyUsed,dailyRemaining,
                    today.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC),daily.rate(),"DAILY");
        }
        Instant reset=YearMonth.from(today).plusMonths(1).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        return new QuotaSnapshot(monthly.limit(),monthlyUsed,monthlyRemaining,reset,monthly.rate(),"MONTHLY");
    }

    private Map<String,Policy> lockPolicies(String role,UUID userId) {
        var rows=jdbc.query("""
                SELECT period,credit_limit,rate_limit_per_minute
                  FROM ai.ai_quota_policies
                 WHERE role=? AND period IN ('DAILY','MONTHLY') AND active=true
                 ORDER BY CASE period WHEN 'DAILY' THEN 0 ELSE 1 END
                 FOR UPDATE
                """,(rs,row)->Map.entry(rs.getString("period"),new Policy(rs.getInt("credit_limit"),rs.getInt("rate_limit_per_minute"))),role);
        Map<String,Policy> policies=new HashMap<>();
        rows.forEach(entry->policies.put(entry.getKey(),entry.getValue()));
        var overrides=jdbc.query("""
                SELECT period,credit_limit FROM ai.ai_quota_overrides
                 WHERE user_id=? AND (expires_at IS NULL OR expires_at>now())
                """,(rs,row)->Map.entry(rs.getString("period"),rs.getInt("credit_limit")),userId);
        overrides.forEach(entry -> {
            Policy base=policies.get(entry.getKey());
            if(base!=null) policies.put(entry.getKey(),new Policy(entry.getValue(),base.rate()));
        });
        return policies;
    }

    private Policy required(Map<String,Policy> policies,String period) {
        Policy policy=policies.get(period);
        if(policy==null) throw new IllegalStateException("No active "+period+" AI quota policy for role");
        return policy;
    }

    private int used(UUID userId,LocalDate from) {
        Integer value=jdbc.queryForObject("""
                SELECT COALESCE(sum(credits),0)::int FROM ai.ai_usage_ledger
                 WHERE user_id=? AND period_day>=? AND state IN ('RESERVED','COMMITTED')
                """,Integer.class,userId,from);
        return value==null?0:value;
    }
    private int usedCommitted(UUID userId,LocalDate from) {
        Integer value=jdbc.queryForObject("""
                SELECT COALESCE(sum(credits),0)::int FROM ai.ai_usage_ledger
                 WHERE user_id=? AND period_day>=? AND state='COMMITTED'
                """,Integer.class,userId,from);
        return value==null?0:value;
    }
}
