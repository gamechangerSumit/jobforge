package com.jobforge.backend.analytics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jobforge.backend.analytics.app.AnalyticsRepository;
import com.jobforge.backend.analytics.app.AnalyticsRepository.AdminCounts;
import com.jobforge.backend.analytics.app.AnalyticsRepository.Funnel;
import com.jobforge.backend.analytics.app.AnalyticsRepository.HireStats;
import com.jobforge.backend.analytics.app.AnalyticsService;
import com.jobforge.backend.analytics.app.AnalyticsViews.AdminOverview;
import com.jobforge.backend.analytics.app.AnalyticsViews.AdminTimeseries;
import com.jobforge.backend.analytics.app.AnalyticsViews.JobAnalytics;
import com.jobforge.backend.analytics.app.AnalyticsViews.RecruiterOverview;
import com.jobforge.backend.analytics.domain.AdminMetric;
import com.jobforge.backend.analytics.domain.Interval;
import com.jobforge.backend.company.facade.CompanyAccessFacade;
import com.jobforge.backend.profile.facade.ProfileFacade;
import com.jobforge.backend.shared.error.BusinessRuleException;
import com.jobforge.backend.shared.error.ForbiddenException;
import com.jobforge.backend.shared.error.ResourceNotFoundException;
import com.jobforge.backend.shared.error.ValidationFailedException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Pure service logic with mocked ports. Written but NOT executed in the authoring session. */
class AnalyticsServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-11T12:00:00Z"), ZoneOffset.UTC);

    private final AnalyticsRepository repo = mock(AnalyticsRepository.class);
    private final CompanyAccessFacade companies = mock(CompanyAccessFacade.class);
    private final ProfileFacade profiles = mock(ProfileFacade.class);
    private final AnalyticsService service = new AnalyticsService(repo, companies, profiles, CLOCK);

    private final UUID recruiter = UUID.randomUUID();
    private final UUID company = UUID.randomUUID();

    @BeforeEach
    void approvedRecruiterWithCompany() {
        when(profiles.isRecruiterApproved(recruiter)).thenReturn(true);
        when(companies.membershipOf(recruiter)).thenReturn(Optional.of(new CompanyAccessFacade.Membership(company, "OWNER")));
    }

    @Test
    void emptyDatasetYieldsZerosAndNullRates() {
        when(repo.pipeline(eq(company), any(), any())).thenReturn(Map.of());
        when(repo.hireStats(eq(company), any(), any())).thenReturn(new HireStats(0, null, null));

        RecruiterOverview o = service.recruiterOverview(recruiter, null, null);

        assertEquals(0, o.applications());
        assertEquals(8, o.pipeline().size());
        assertEquals(0L, o.pipeline().get("WITHDRAWN"));
        assertNull(o.conversion().rate());
        assertNull(o.timeToHire().averageDays());
        assertNull(o.timeToHire().medianDays());
        assertEquals("2026-10-11", o.to());
        assertEquals("2026-09-12", o.from());
    }

    @Test
    void pipelineSumsToApplicationsAndRateIsRoundedToFourDecimals() {
        Map<String, Long> raw = new LinkedHashMap<>();
        raw.put("SUBMITTED", 10L);
        raw.put("HIRED", 3L);
        raw.put("REJECTED", 29L);
        when(repo.pipeline(eq(company), any(), any())).thenReturn(raw);
        when(repo.hireStats(eq(company), any(), any())).thenReturn(new HireStats(3, 12.449, 11.04));

        RecruiterOverview o = service.recruiterOverview(recruiter, "2026-09-12", "2026-10-11");

        assertEquals(42, o.applications());
        assertEquals(0.0714, o.conversion().rate());
        assertEquals(3, o.conversion().hired());
        assertEquals(12.4, o.timeToHire().averageDays());
        assertEquals(11.0, o.timeToHire().medianDays());
    }

    @Test
    void conversionIncludesHiredApplicationsWithoutHistoryButTimeToHireDoesNot() {
        when(repo.pipeline(eq(company), any(), any())).thenReturn(Map.of("HIRED", 3L, "SUBMITTED", 2L));
        when(repo.hireStats(eq(company), any(), any())).thenReturn(new HireStats(2, 10.0, 10.0));

        RecruiterOverview o = service.recruiterOverview(recruiter, "2026-09-12", "2026-10-11");

        assertEquals(3, o.conversion().hired());
        assertEquals(0.6, o.conversion().rate());
        assertEquals(2, o.timeToHire().hired());
        assertEquals(10.0, o.timeToHire().averageDays());
    }

    @Test
    void unapprovedRecruiterIsForbiddenAndNothingIsQueried() {
        when(profiles.isRecruiterApproved(recruiter)).thenReturn(false);
        assertThrows(ForbiddenException.class, () -> service.recruiterOverview(recruiter, null, null));
        verify(repo, never()).pipeline(any(), any(), any());
    }

    @Test
    void recruiterWithoutCompanyIsABusinessRuleViolation() {
        when(companies.membershipOf(recruiter)).thenReturn(Optional.empty());
        assertThrows(BusinessRuleException.class, () -> service.recruiterOverview(recruiter, null, null));
    }

    @Test
    void invalidRangeFailsBeforeAnyAccessCheck() {
        assertThrows(ValidationFailedException.class, () -> service.recruiterOverview(recruiter, "2026-10-05", "2026-10-01"));
        verify(profiles, never()).isRecruiterApproved(any());
    }

    @Test
    void jobOfAnotherCompanyIsNotFound() {
        UUID job = UUID.randomUUID();
        when(repo.jobBelongsToCompany(job, company)).thenReturn(false);
        assertThrows(ResourceNotFoundException.class, () -> service.jobAnalytics(recruiter, job, null, null));
        verify(repo, never()).funnel(any(), any(), any());
    }

    @Test
    void jobAnalyticsZeroFillsSeriesAndKeepsFunnelOrder() {
        UUID job = UUID.randomUUID();
        when(repo.jobBelongsToCompany(job, company)).thenReturn(true);
        when(repo.funnel(eq(job), any(), any())).thenReturn(new Funnel(new long[] {17, 12, 6, 4, 2, 1}, 5, 2));
        when(repo.dailyApplications(eq(job), any(), any())).thenReturn(Map.of(LocalDate.parse("2026-10-02"), 3L));

        JobAnalytics a = service.jobAnalytics(recruiter, job, "2026-10-01", "2026-10-03");

        assertEquals(17, a.applications());
        assertEquals("SUBMITTED", a.funnel().get(0).stage());
        assertEquals("HIRED", a.funnel().get(5).stage());
        assertEquals(1, a.funnel().get(5).count());
        assertEquals(5L, a.outcomes().get("REJECTED"));
        assertEquals(3, a.series().size());
        assertEquals(0, a.series().get(0).applications());
        assertEquals(3, a.series().get(1).applications());
        assertEquals("2026-10-03", a.series().get(2).date());
    }

    @Test
    void adminOverviewTotalsRoles() {
        Map<String, Long> users = Map.of("JOB_SEEKER", 100L, "RECRUITER", 19L, "ADMIN", 1L);
        when(repo.adminCounts(any(), any(), any())).thenReturn(new AdminCounts(users, 55, 31, 410, 12, 9, 3));
        AdminOverview o = service.adminOverview(null, null);
        assertEquals(120, o.users().registered());
        assertEquals(31, o.jobs().currentlyOpen());
        assertEquals(12, o.applications().hired());
        assertEquals(3, o.reports().open());
    }

    @Test
    void timeseriesZeroFillsAndMergesClippedFirstBucket() {
        Map<LocalDate, Long> raw = new LinkedHashMap<>();
        raw.put(LocalDate.parse("2026-10-05"), 3L); // Monday bucket that starts before `from`
        raw.put(LocalDate.parse("2026-10-19"), 2L);
        when(repo.adminSeries(eq(AdminMetric.APPLICATIONS_SUBMITTED), eq(Interval.WEEK), any(), any())).thenReturn(raw);

        AdminTimeseries t = service.adminTimeseries(AdminMetric.APPLICATIONS_SUBMITTED, Interval.WEEK, "2026-10-07", "2026-10-20");

        assertEquals(3, t.points().size());
        assertEquals("2026-10-07", t.points().get(0).date());
        assertEquals(3, t.points().get(0).value());
        assertEquals(0, t.points().get(1).value());
        assertEquals(2, t.points().get(2).value());
    }

    @Test
    void timeseriesDefaultsToDayAndRequiresMetric() {
        when(repo.adminSeries(any(), any(), any(), any())).thenReturn(Map.of());
        assertEquals("DAY", service.adminTimeseries(AdminMetric.USERS_REGISTERED, null, "2026-10-01", "2026-10-02").interval());
        assertThrows(ValidationFailedException.class, () -> service.adminTimeseries(null, Interval.DAY, null, null));
    }
}
