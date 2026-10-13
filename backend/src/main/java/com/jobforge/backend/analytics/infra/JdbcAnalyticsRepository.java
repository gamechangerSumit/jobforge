package com.jobforge.backend.analytics.infra;

import com.jobforge.backend.analytics.app.AnalyticsRepository;
import com.jobforge.backend.analytics.domain.AdminMetric;
import com.jobforge.backend.analytics.domain.Interval;
import com.jobforge.backend.shared.persistence.Db;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Aggregate queries over core.* tables (DATABASE_SCHEMA §4). All SQL is constant text with bound parameters; the only
 * variable pieces (metric column, bucket unit) come from whitelisted enums. Every count is over {@code applications}
 * rows (or {@code jobs}/{@code users}/{@code reports} rows) of the cohort, joined only to single-row parents, or
 * through correlated sub-selects, so a join can never multiply a count. Ranges are {@code [from, toExclusive)}.
 */
@Repository
public class JdbcAnalyticsRepository implements AnalyticsRepository {

    /** Funnel rank of an application status; terminal statuses rank 0. */
    private static String rank(String column) {
        return "CASE " + column + " WHEN 'SUBMITTED' THEN 1 WHEN 'UNDER_REVIEW' THEN 2 WHEN 'SHORTLISTED' THEN 3 "
                + "WHEN 'INTERVIEW' THEN 4 WHEN 'OFFERED' THEN 5 WHEN 'HIRED' THEN 6 ELSE 0 END";
    }

    private static final Map<AdminMetric, String> SERIES_SQL = new EnumMap<>(AdminMetric.class);

    static {
        SERIES_SQL.put(AdminMetric.USERS_REGISTERED, """
                SELECT CAST(date_trunc(CAST(:unit AS text), created_at AT TIME ZONE 'UTC') AS date) AS bucket, count(*) AS value
                FROM core.users
                WHERE created_at >= :from AND created_at < :to
                GROUP BY 1 ORDER BY 1
                """);
        SERIES_SQL.put(AdminMetric.JOBS_PUBLISHED, """
                SELECT CAST(date_trunc(CAST(:unit AS text), published_at AT TIME ZONE 'UTC') AS date) AS bucket, count(*) AS value
                FROM core.jobs
                WHERE deleted_at IS NULL AND published_at >= :from AND published_at < :to
                GROUP BY 1 ORDER BY 1
                """);
        SERIES_SQL.put(AdminMetric.APPLICATIONS_SUBMITTED, """
                SELECT CAST(date_trunc(CAST(:unit AS text), applied_at AT TIME ZONE 'UTC') AS date) AS bucket, count(*) AS value
                FROM core.applications
                WHERE applied_at >= :from AND applied_at < :to
                GROUP BY 1 ORDER BY 1
                """);
    }

    private final JdbcClient jdbc;

    public JdbcAnalyticsRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<String, Long> pipeline(UUID companyId, Instant from, Instant toExclusive) {
        Map<String, Long> result = new LinkedHashMap<>();
        jdbc.sql("""
                SELECT a.status AS status, count(*) AS total
                FROM core.applications a
                JOIN core.jobs j ON j.id = a.job_id
                WHERE j.company_id = :company AND j.deleted_at IS NULL
                  AND a.applied_at >= :from AND a.applied_at < :to
                GROUP BY a.status
                """)
                .param("company", companyId).param("from", Db.ts(from)).param("to", Db.ts(toExclusive))
                .query((rs, n) -> {
                    result.put(rs.getString("status"), rs.getLong("total"));
                    return null;
                }).list();
        return result;
    }

    @Override
    public HireStats hireStats(UUID companyId, Instant from, Instant toExclusive) {
        // Only applications with a real HIRED history row contribute to time-to-hire. The LATERAL sub-select
        // returns at most one row per application, so history rows cannot multiply counts.
        return jdbc.sql("""
                SELECT count(*) AS hired,
                       avg(d.days) AS average_days,
                       percentile_cont(0.5) WITHIN GROUP (ORDER BY d.days) AS median_days
                FROM (
                    SELECT CAST(EXTRACT(EPOCH FROM (h.hired_at - a.applied_at)) / 86400.0 AS double precision) AS days
                    FROM core.applications a
                    JOIN core.jobs j ON j.id = a.job_id
                    JOIN LATERAL (
                        SELECT min(s.created_at) AS hired_at
                        FROM core.application_status_history s
                        WHERE s.application_id = a.id AND s.to_status = 'HIRED'
                    ) h ON h.hired_at IS NOT NULL
                    WHERE j.company_id = :company AND j.deleted_at IS NULL
                      AND a.status = 'HIRED'
                      AND a.applied_at >= :from AND a.applied_at < :to
                ) d
                """)
                .param("company", companyId).param("from", Db.ts(from)).param("to", Db.ts(toExclusive))
                .query((rs, n) -> new HireStats(rs.getLong("hired"), nullableDouble(rs, "average_days"),
                        nullableDouble(rs, "median_days")))
                .single();
    }

    @Override
    public long jobsPublished(UUID companyId, Instant from, Instant toExclusive) {
        return jdbc.sql("""
                SELECT count(*) FROM core.jobs
                WHERE company_id = :company AND deleted_at IS NULL
                  AND published_at >= :from AND published_at < :to
                """)
                .param("company", companyId).param("from", Db.ts(from)).param("to", Db.ts(toExclusive))
                .query(Long.class).single();
    }

    @Override
    public long jobsOpen(UUID companyId, Instant now) {
        return jdbc.sql("""
                SELECT count(*) FROM core.jobs
                WHERE company_id = :company AND deleted_at IS NULL AND status = 'PUBLISHED'
                  AND (expires_at IS NULL OR expires_at > :now)
                """)
                .param("company", companyId).param("now", Db.ts(now))
                .query(Long.class).single();
    }

    @Override
    public boolean jobBelongsToCompany(UUID jobId, UUID companyId) {
        return jdbc.sql("SELECT count(*) FROM core.jobs WHERE id = :job AND company_id = :company AND deleted_at IS NULL")
                .param("job", jobId).param("company", companyId)
                .query(Long.class).single() > 0;
    }

    @Override
    public Funnel funnel(UUID jobId, Instant from, Instant toExclusive) {
        // reached = highest funnel rank of the current status or any history row; every application is at least SUBMITTED.
        String sql = """
                SELECT count(*) FILTER (WHERE r >= 1) AS s1, count(*) FILTER (WHERE r >= 2) AS s2,
                       count(*) FILTER (WHERE r >= 3) AS s3, count(*) FILTER (WHERE r >= 4) AS s4,
                       count(*) FILTER (WHERE r >= 5) AS s5, count(*) FILTER (WHERE r >= 6) AS s6,
                       count(*) FILTER (WHERE status = 'REJECTED') AS rejected,
                       count(*) FILTER (WHERE status = 'WITHDRAWN') AS withdrawn
                FROM (
                    SELECT a.status AS status,
                           GREATEST(1, %s,
                                    COALESCE((SELECT max(%s) FROM core.application_status_history s
                                              WHERE s.application_id = a.id), 0)) AS r
                    FROM core.applications a
                    WHERE a.job_id = :job AND a.applied_at >= :from AND a.applied_at < :to
                ) c
                """.formatted(rank("a.status"), rank("s.to_status"));
        return jdbc.sql(sql)
                .param("job", jobId).param("from", Db.ts(from)).param("to", Db.ts(toExclusive))
                .query((rs, n) -> new Funnel(new long[] {rs.getLong("s1"), rs.getLong("s2"), rs.getLong("s3"),
                        rs.getLong("s4"), rs.getLong("s5"), rs.getLong("s6")}, rs.getLong("rejected"),
                        rs.getLong("withdrawn")))
                .single();
    }

    @Override
    public Map<LocalDate, Long> dailyApplications(UUID jobId, Instant from, Instant toExclusive) {
        Map<LocalDate, Long> result = new LinkedHashMap<>();
        jdbc.sql("""
                SELECT CAST(applied_at AT TIME ZONE 'UTC' AS date) AS day, count(*) AS total
                FROM core.applications
                WHERE job_id = :job AND applied_at >= :from AND applied_at < :to
                GROUP BY 1 ORDER BY 1
                """)
                .param("job", jobId).param("from", Db.ts(from)).param("to", Db.ts(toExclusive))
                .query((rs, n) -> {
                    result.put(rs.getObject("day", LocalDate.class), rs.getLong("total"));
                    return null;
                }).list();
        return result;
    }

    @Override
    public AdminCounts adminCounts(Instant from, Instant toExclusive, Instant now) {
        Map<String, Long> users = new LinkedHashMap<>();
        jdbc.sql("""
                SELECT role, count(*) AS total FROM core.users
                WHERE created_at >= :from AND created_at < :to
                GROUP BY role
                """)
                .param("from", Db.ts(from)).param("to", Db.ts(toExclusive))
                .query((rs, n) -> {
                    users.put(rs.getString("role"), rs.getLong("total"));
                    return null;
                }).list();
        return jdbc.sql("""
                SELECT
                  (SELECT count(*) FROM core.jobs WHERE deleted_at IS NULL AND published_at >= :from AND published_at < :to) AS jobs_published,
                  (SELECT count(*) FROM core.jobs WHERE deleted_at IS NULL AND status = 'PUBLISHED'
                                                     AND (expires_at IS NULL OR expires_at > :now)) AS jobs_open,
                  (SELECT count(*) FROM core.applications WHERE applied_at >= :from AND applied_at < :to) AS apps_submitted,
                  (SELECT count(*) FROM core.applications WHERE applied_at >= :from AND applied_at < :to AND status = 'HIRED') AS apps_hired,
                  (SELECT count(*) FROM core.reports WHERE created_at >= :from AND created_at < :to) AS reports_filed,
                  (SELECT count(*) FROM core.reports WHERE status IN ('OPEN', 'REVIEWING')) AS reports_open
                """)
                .param("from", Db.ts(from)).param("to", Db.ts(toExclusive)).param("now", Db.ts(now))
                .query((rs, n) -> new AdminCounts(users, rs.getLong("jobs_published"), rs.getLong("jobs_open"),
                        rs.getLong("apps_submitted"), rs.getLong("apps_hired"), rs.getLong("reports_filed"),
                        rs.getLong("reports_open")))
                .single();
    }

    @Override
    public Map<LocalDate, Long> adminSeries(AdminMetric metric, Interval interval, Instant from, Instant toExclusive) {
        Map<LocalDate, Long> result = new LinkedHashMap<>();
        jdbc.sql(SERIES_SQL.get(metric))
                .param("unit", interval.sqlUnit()).param("from", Db.ts(from)).param("to", Db.ts(toExclusive))
                .query((rs, n) -> {
                    result.put(rs.getObject("bucket", LocalDate.class), rs.getLong("value"));
                    return null;
                }).list();
        return result;
    }

    private static Double nullableDouble(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }
}
