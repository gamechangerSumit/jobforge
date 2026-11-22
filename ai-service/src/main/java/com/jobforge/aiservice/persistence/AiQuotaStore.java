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
    private final JdbcTemplate jdbc;

    public AiQuotaStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public QuotaSnapshot reserve(UUID userId, String role, UUID requestId, String operation, int credits) {
        Map<String,Policy> policies=lockPolicies(role);
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

    public QuotaSnapshot snapshot(UUID userId, String role) {
        Map<String,Policy> policies=lockPolicies(role);
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

    private Map<String,Policy> lockPolicies(String role) {
        var rows=jdbc.query("""
                SELECT period,credit_limit,rate_limit_per_minute
                  FROM ai.ai_quota_policies
                 WHERE role=? AND period IN ('DAILY','MONTHLY') AND active=true
                 ORDER BY CASE period WHEN 'DAILY' THEN 0 ELSE 1 END
                 FOR UPDATE
                """,(rs,row)->Map.entry(rs.getString("period"),new Policy(rs.getInt("credit_limit"),rs.getInt("rate_limit_per_minute"))),role);
        Map<String,Policy> policies=new HashMap<>();
        rows.forEach(entry->policies.put(entry.getKey(),entry.getValue()));
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
