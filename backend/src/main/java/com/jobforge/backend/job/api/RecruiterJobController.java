package com.jobforge.backend.job.api;

import com.jobforge.backend.job.app.JobService;
import com.jobforge.backend.job.app.RecruiterJobItem;
import com.jobforge.backend.job.domain.JobStatus;
import com.jobforge.backend.shared.api.PagedResponse;
import com.jobforge.backend.shared.security.AuthenticatedUser;
import com.jobforge.backend.shared.web.QueryParams;
import jakarta.servlet.http.HttpServletRequest;
import java.util.function.Function;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET /recruiters/me/jobs}: the caller's company jobs in all statuses (RC-2). */
@RestController
@RequestMapping("/recruiters/me/jobs")
@PreAuthorize("hasRole('RECRUITER')")
public class RecruiterJobController {

    private final JobService jobs;

    public RecruiterJobController(JobService jobs) {
        this.jobs = jobs;
    }

    @GetMapping
    public PagedResponse<RecruiterJobItem> list(@AuthenticationPrincipal AuthenticatedUser caller, HttpServletRequest request) {
        QueryParams q = new QueryParams(request, "status", "q", "page", "size");
        int page = q.page();
        int size = q.size();
        return jobs.listCompanyJobs(caller, q.enumValue("status", JobStatus.class), q.string("q"), page, size)
                .toResponse(Function.identity(), page, size);
    }
}
