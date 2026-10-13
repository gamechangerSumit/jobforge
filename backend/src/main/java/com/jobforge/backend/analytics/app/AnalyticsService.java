package com.jobforge.backend.analytics.app;

import com.jobforge.backend.analytics.app.AnalyticsRepository.AdminCounts;
import com.jobforge.backend.analytics.app.AnalyticsRepository.Funnel;
import com.jobforge.backend.analytics.app.AnalyticsRepository.HireStats;
import com.jobforge.backend.analytics.app.AnalyticsViews.AdminApplications;
import com.jobforge.backend.analytics.app.AnalyticsViews.AdminJobs;
import com.jobforge.backend.analytics.app.AnalyticsViews.AdminOverview;
import com.jobforge.backend.analytics.app.AnalyticsViews.AdminReports;
import com.jobforge.backend.analytics.app.AnalyticsViews.AdminTimeseries;
import com.jobforge.backend.analytics.app.AnalyticsViews.AdminUsers;
import com.jobforge.backend.analytics.app.AnalyticsViews.Conversion;
import com.jobforge.backend.analytics.app.AnalyticsViews.FunnelPoint;
import com.jobforge.backend.analytics.app.AnalyticsViews.JobAnalytics;
import com.jobforge.backend.analytics.app.AnalyticsViews.JobCounts;
import com.jobforge.backend.analytics.app.AnalyticsViews.RecruiterOverview;
import com.jobforge.backend.analytics.app.AnalyticsViews.SeriesPoint;
import com.jobforge.backend.analytics.app.AnalyticsViews.TimePoint;
import com.jobforge.backend.analytics.app.AnalyticsViews.TimeToHire;
import com.jobforge.backend.analytics.domain.AdminMetric;
import com.jobforge.backend.analytics.domain.FunnelStage;
import com.jobforge.backend.analytics.domain.Interval;
import com.jobforge.backend.company.facade.CompanyAccessFacade;
import com.jobforge.backend.profile.facade.ProfileFacade;
import com.jobforge.backend.shared.error.BusinessRuleException;
import com.jobforge.backend.shared.error.ErrorCode;
import com.jobforge.backend.shared.error.ForbiddenException;
import com.jobforge.backend.shared.error.ResourceNotFoundException;
import com.jobforge.backend.shared.error.ValidationFailedException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Proposed (Session 5, unmerged contract change; API_CONTRACT §12.10.1 and §12.11.2): analytics use cases. Read-only; tenant scope comes from the caller's company
 * membership, never from a request parameter.
 */
@Service
public class AnalyticsService {

    /** Every application status, in funnel-then-terminal order (the pipeline always carries all eight keys). */
    static final List<String> STATUSES = List.of("SUBMITTED", "UNDER_REVIEW", "SHORTLISTED", "INTERVIEW", "OFFERED",
            "HIRED", "REJECTED", "WITHDRAWN");

    private final AnalyticsRepository repository;
    private final CompanyAccessFacade companies;
    private final ProfileFacade profiles;
    private final Clock clock;

    public AnalyticsService(AnalyticsRepository repository, CompanyAccessFacade companies, ProfileFacade profiles,
            Clock clock) {
        this.repository = repository;
        this.companies = companies;
        this.profiles = profiles;
        this.clock = clock;
    }

    public LocalDate today() {
        return LocalDate.now(clock.withZone(ZoneOffset.UTC));
    }

    // ---------------------------------------------------------------- recruiter

    public RecruiterOverview recruiterOverview(UUID recruiterId, String fromRaw, String toRaw) {
        DateRange range = DateRange.resolve(fromRaw, toRaw, today());
        UUID company = requireCompany(recruiterId);
        Instant from = range.fromInstant();
        Instant to = range.toExclusiveInstant();

        Map<String, Long> raw = repository.pipeline(company, from, to);
        Map<String, Long> pipeline = new LinkedHashMap<>();
        long applications = 0;
        for (String status : STATUSES) {
            long count = raw.getOrDefault(status, 0L);
            pipeline.put(status, count);
            applications += count;
        }
        HireStats hires = repository.hireStats(company, from, to);
        long hiredApplications = pipeline.getOrDefault("HIRED", 0L);
        Conversion conversion = new Conversion(hiredApplications, rate(hiredApplications, applications));
        TimeToHire timeToHire = new TimeToHire(hires.hired(), round(hires.averageDays(), 1), round(hires.medianDays(), 1));
        JobCounts jobs = new JobCounts(repository.jobsPublished(company, from, to),
                repository.jobsOpen(company, clock.instant()));
        return new RecruiterOverview(range.from().toString(), range.to().toString(), applications, pipeline, conversion,
                timeToHire, jobs);
    }

    public JobAnalytics jobAnalytics(UUID recruiterId, UUID jobId, String fromRaw, String toRaw) {
        DateRange range = DateRange.resolve(fromRaw, toRaw, today());
        UUID company = requireCompany(recruiterId);
        if (!repository.jobBelongsToCompany(jobId, company)) {
            throw new ResourceNotFoundException("Job not found.");
        }
        Instant from = range.fromInstant();
        Instant to = range.toExclusiveInstant();

        Funnel funnel = repository.funnel(jobId, from, to);
        List<FunnelPoint> points = new ArrayList<>();
        for (FunnelStage stage : FunnelStage.ORDER) {
            points.add(new FunnelPoint(stage.name(), funnel.reached()[stage.ordinal()]));
        }
        Map<String, Long> outcomes = new LinkedHashMap<>();
        outcomes.put("REJECTED", funnel.rejected());
        outcomes.put("WITHDRAWN", funnel.withdrawn());

        Map<LocalDate, Long> daily = repository.dailyApplications(jobId, from, to);
        List<SeriesPoint> series = new ArrayList<>();
        for (LocalDate day : range.days()) {
            series.add(new SeriesPoint(day.toString(), daily.getOrDefault(day, 0L)));
        }
        return new JobAnalytics(jobId, range.from().toString(), range.to().toString(), funnel.reached()[0], points,
                outcomes, series);
    }

    private UUID requireCompany(UUID recruiterId) {
        if (!profiles.isRecruiterApproved(recruiterId)) {
            throw new ForbiddenException(ErrorCode.RECRUITER_NOT_APPROVED, "Your recruiter account is not approved yet.");
        }
        return companies.membershipOf(recruiterId).map(CompanyAccessFacade.Membership::companyId)
                .orElseThrow(() -> new BusinessRuleException("Create a company before viewing analytics."));
    }

    // -------------------------------------------------------------------- admin

    public AdminOverview adminOverview(String fromRaw, String toRaw) {
        DateRange range = DateRange.resolve(fromRaw, toRaw, today());
        AdminCounts c = repository.adminCounts(range.fromInstant(), range.toExclusiveInstant(), clock.instant());
        long seekers = c.usersByRole().getOrDefault("JOB_SEEKER", 0L);
        long recruiters = c.usersByRole().getOrDefault("RECRUITER", 0L);
        long admins = c.usersByRole().getOrDefault("ADMIN", 0L);
        return new AdminOverview(range.from().toString(), range.to().toString(),
                new AdminUsers(seekers + recruiters + admins, seekers, recruiters, admins),
                new AdminJobs(c.jobsPublished(), c.jobsOpen()),
                new AdminApplications(c.applicationsSubmitted(), c.applicationsHired()),
                new AdminReports(c.reportsFiled(), c.reportsOpen()));
    }

    public AdminTimeseries adminTimeseries(AdminMetric metric, Interval interval, String fromRaw, String toRaw) {
        if (metric == null) {
            throw ValidationFailedException.of("metric", "NOT_BLANK", "is required");
        }
        Interval effective = interval == null ? Interval.DAY : interval;
        DateRange range = DateRange.resolve(fromRaw, toRaw, today());
        if (effective.bucketCount(range.from(), range.to()) > Interval.MAX_BUCKETS) {
            throw ValidationFailedException.of("interval", "MAX", "range yields more than " + Interval.MAX_BUCKETS + " buckets");
        }
        Map<LocalDate, Long> raw = repository.adminSeries(metric, effective, range.fromInstant(),
                range.toExclusiveInstant());
        // The database returns bucket starts; the first bucket may start before `from`, so re-key like the zero-fill keys.
        Map<LocalDate, Long> byKey = new LinkedHashMap<>();
        raw.forEach((start, value) -> byKey.merge(effective.key(start, range.from()), value, Long::sum));
        List<TimePoint> points = new ArrayList<>();
        for (LocalDate key : effective.keys(range.from(), range.to())) {
            points.add(new TimePoint(key.toString(), byKey.getOrDefault(key, 0L)));
        }
        return new AdminTimeseries(metric.name(), effective.name(), range.from().toString(), range.to().toString(), points);
    }

    // ------------------------------------------------------------------ helpers

    /** hired ÷ applications, 4 decimals; null for an empty cohort. */
    static Double rate(long hired, long applications) {
        return applications == 0 ? null : Math.round((double) hired / applications * 10_000d) / 10_000d;
    }

    static Double round(Double value, int decimals) {
        if (value == null) {
            return null;
        }
        double scale = Math.pow(10, decimals);
        return Math.round(value * scale) / scale;
    }
}
