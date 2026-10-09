package com.jobforge.backend.search.api;

import com.jobforge.backend.search.app.JobSearchService;
import com.jobforge.backend.search.domain.JobFacets;
import com.jobforge.backend.search.domain.JobSearchResult;
import com.jobforge.backend.shared.api.PagedResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import com.jobforge.backend.shared.security.AuthenticatedUser;
import com.jobforge.backend.shared.domain.UserRole;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API_CONTRACT 12.4 public job discovery (search, facets, similar). All endpoints are anonymous-readable. */
@RestController
@RequestMapping("/jobs")
public class JobSearchController {

    private final JobSearchService service;
    private final Clock clock;

    public JobSearchController(JobSearchService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    @GetMapping
    public PagedResponse<JobSearchResult> search(HttpServletRequest request, @AuthenticationPrincipal AuthenticatedUser caller) {
        return service.search(JobSearchFilterFactory.from(request, clock, true), seekerId(caller));
    }

    @GetMapping("/facets")
    public JobFacets facets(HttpServletRequest request) {
        return service.facets(JobSearchFilterFactory.from(request, clock, false));
    }

    @GetMapping("/{id}/similar")
    public List<JobSearchResult> similar(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser caller) {
        return service.similar(id, seekerId(caller));
    }

    /** Flags are per seeker; anonymous callers and other roles get {@code null} flags. */
    private static UUID seekerId(AuthenticatedUser caller) {
        return caller != null && caller.role() == UserRole.JOB_SEEKER ? caller.id() : null;
    }
}
