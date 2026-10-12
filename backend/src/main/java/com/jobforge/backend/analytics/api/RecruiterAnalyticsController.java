package com.jobforge.backend.analytics.api;

import com.jobforge.backend.analytics.app.AnalyticsService;
import com.jobforge.backend.analytics.app.AnalyticsViews.JobAnalytics;
import com.jobforge.backend.analytics.app.AnalyticsViews.RecruiterOverview;
import com.jobforge.backend.shared.security.AuthenticatedUser;
import com.jobforge.backend.shared.web.QueryParams;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Proposed (Session 5, unmerged contract change; API_CONTRACT §12.10 / §12.10.1): recruiter analytics. RECRUITER only; the company is derived from the caller's
 * membership (never a parameter), so one company can never read another's numbers.
 */
@RestController
public class RecruiterAnalyticsController {

    private final AnalyticsService analytics;

    public RecruiterAnalyticsController(AnalyticsService analytics) {
        this.analytics = analytics;
    }

    @GetMapping("/recruiters/me/analytics/overview")
    @PreAuthorize("hasRole('RECRUITER')")
    public RecruiterOverview overview(@AuthenticationPrincipal AuthenticatedUser caller, HttpServletRequest http) {
        QueryParams q = new QueryParams(http, "from", "to");
        return analytics.recruiterOverview(caller.id(), q.string("from"), q.string("to"));
    }

    @GetMapping("/recruiters/me/analytics/jobs/{jobId}")
    @PreAuthorize("hasRole('RECRUITER')")
    public JobAnalytics job(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID jobId,
            HttpServletRequest http) {
        QueryParams q = new QueryParams(http, "from", "to");
        return analytics.jobAnalytics(caller.id(), jobId, q.string("from"), q.string("to"));
    }
}
