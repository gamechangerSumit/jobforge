package com.jobforge.backend.analytics.app;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Response DTOs for the proposed, unmerged Session 5 contract change (API_CONTRACT §12.10.1 / §12.11.2). Aggregates only: no applicant or user identity. */
public final class AnalyticsViews {

    private AnalyticsViews() {}

    public record Conversion(long hired, Double rate) {}

    public record TimeToHire(long hired, Double averageDays, Double medianDays) {}

    public record JobCounts(long published, long open) {}

    /** {@code pipeline} is keyed by every application status (ordered, zero-filled). */
    public record RecruiterOverview(String from, String to, long applications, Map<String, Long> pipeline,
            Conversion conversion, TimeToHire timeToHire, JobCounts jobs) {}

    public record FunnelPoint(String stage, long count) {}

    public record SeriesPoint(String date, long applications) {}

    public record JobAnalytics(UUID jobId, String from, String to, long applications, List<FunnelPoint> funnel,
            Map<String, Long> outcomes, List<SeriesPoint> series) {}

    public record AdminUsers(long registered, long jobSeekers, long recruiters, long admins) {}

    public record AdminJobs(long published, long currentlyOpen) {}

    public record AdminApplications(long submitted, long hired) {}

    public record AdminReports(long filed, long open) {}

    public record AdminOverview(String from, String to, AdminUsers users, AdminJobs jobs,
            AdminApplications applications, AdminReports reports) {}

    public record TimePoint(String date, long value) {}

    public record AdminTimeseries(String metric, String interval, String from, String to, List<TimePoint> points) {}
}
