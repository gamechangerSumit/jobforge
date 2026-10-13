package com.jobforge.backend.analytics.app;

import com.jobforge.backend.analytics.domain.AdminMetric;
import com.jobforge.backend.analytics.domain.Interval;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Read-only SQL aggregates (no entity loading). All ranges are {@code [from, toExclusive)} instants. Every metric
 * counts each application at most once (no join multiplication).
 */
public interface AnalyticsRepository {

    record HireStats(long hired, Double averageDays, Double medianDays) {}

    /** Reached-stage counts, index 0 = SUBMITTED … index 5 = HIRED, plus terminal outcomes. */
    record Funnel(long[] reached, long rejected, long withdrawn) {}

    record AdminCounts(Map<String, Long> usersByRole, long jobsPublished, long jobsOpen, long applicationsSubmitted,
            long applicationsHired, long reportsFiled, long reportsOpen) {}

    /** Cohort applications of the company's non-deleted jobs, by current status (only statuses present). */
    Map<String, Long> pipeline(UUID companyId, Instant from, Instant toExclusive);

    HireStats hireStats(UUID companyId, Instant from, Instant toExclusive);

    long jobsPublished(UUID companyId, Instant from, Instant toExclusive);

    long jobsOpen(UUID companyId, Instant now);

    /** True only for a non-deleted job of that company. */
    boolean jobBelongsToCompany(UUID jobId, UUID companyId);

    Funnel funnel(UUID jobId, Instant from, Instant toExclusive);

    /** UTC day → cohort applications of that job applied that day (days without applications are absent). */
    Map<LocalDate, Long> dailyApplications(UUID jobId, Instant from, Instant toExclusive);

    AdminCounts adminCounts(Instant from, Instant toExclusive, Instant now);

    /** Bucket start date (UTC, as computed by the database) → value; empty buckets are absent. */
    Map<LocalDate, Long> adminSeries(AdminMetric metric, Interval interval, Instant from, Instant toExclusive);
}
