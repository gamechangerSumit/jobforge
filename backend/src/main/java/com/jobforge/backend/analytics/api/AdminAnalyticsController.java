package com.jobforge.backend.analytics.api;

import com.jobforge.backend.analytics.app.AnalyticsService;
import com.jobforge.backend.analytics.app.AnalyticsViews.AdminOverview;
import com.jobforge.backend.analytics.app.AnalyticsViews.AdminTimeseries;
import com.jobforge.backend.analytics.domain.AdminMetric;
import com.jobforge.backend.analytics.domain.Interval;
import com.jobforge.backend.shared.web.QueryParams;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Proposed (Session 5, unmerged contract change; API_CONTRACT §12.11.2): platform analytics. ADMIN only (URL rule {@code /admin/**} plus {@code @PreAuthorize}). */
@RestController
@RequestMapping("/admin/analytics")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAnalyticsController {

    private final AnalyticsService analytics;

    public AdminAnalyticsController(AnalyticsService analytics) {
        this.analytics = analytics;
    }

    @GetMapping("/overview")
    public AdminOverview overview(HttpServletRequest http) {
        QueryParams q = new QueryParams(http, "from", "to");
        return analytics.adminOverview(q.string("from"), q.string("to"));
    }

    @GetMapping("/timeseries")
    public AdminTimeseries timeseries(HttpServletRequest http) {
        QueryParams q = new QueryParams(http, "metric", "from", "to", "interval");
        return analytics.adminTimeseries(q.enumValue("metric", AdminMetric.class), q.enumValue("interval", Interval.class),
                q.string("from"), q.string("to"));
    }
}
