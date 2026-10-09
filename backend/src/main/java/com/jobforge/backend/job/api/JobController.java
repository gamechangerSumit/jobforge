package com.jobforge.backend.job.api;

import com.jobforge.backend.job.app.JobDetailView;
import com.jobforge.backend.job.app.JobService;
import com.jobforge.backend.job.facade.JobFacade.Viewer;
import com.jobforge.backend.shared.config.ApiPaths;
import com.jobforge.backend.shared.security.AuthenticatedUser;
import com.jobforge.backend.shared.web.IfMatch;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API_CONTRACT §12.4. Search (GET /jobs, /jobs/facets, /jobs/{id}/similar) belongs to the search module (Dev 3). */
@RestController
@RequestMapping("/jobs")
public class JobController {

    private final JobService jobs;

    public JobController(JobService jobs) {
        this.jobs = jobs;
    }

    /** Public for PUBLISHED jobs; members/admin may see other statuses. Bearer token is optional. */
    @GetMapping("/{id}")
    public ResponseEntity<JobDetailView> detail(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser caller) {
        Viewer viewer = caller == null ? Viewer.ANONYMOUS : new Viewer(caller.id(), caller.role());
        return etag(jobs.getDetail(id, viewer));
    }

    @PostMapping
    @PreAuthorize("hasRole('RECRUITER')")
    public ResponseEntity<JobDetailView> create(@AuthenticationPrincipal AuthenticatedUser caller,
            @Valid @RequestBody JobRequests.Create request) {
        JobDetailView created = jobs.create(caller, request.toDraft());
        return ResponseEntity.created(URI.create(ApiPaths.BASE + "/jobs/" + created.id()))
                .eTag("\"" + created.version() + "\"").body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('RECRUITER')")
    public ResponseEntity<JobDetailView> update(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @Valid @RequestBody JobRequests.Patch request) {
        return etag(jobs.update(caller, id, request.toDraft(), IfMatch.parse(ifMatch)));
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasRole('RECRUITER')")
    public ResponseEntity<JobDetailView> publish(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id,
            @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        return etag(jobs.publish(caller, id, IfMatch.parse(ifMatch)));
    }

    @PostMapping("/{id}/unpublish")
    @PreAuthorize("hasRole('RECRUITER')")
    public ResponseEntity<JobDetailView> unpublish(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id,
            @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        return etag(jobs.unpublish(caller, id, IfMatch.parse(ifMatch)));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasRole('RECRUITER')")
    public ResponseEntity<JobDetailView> close(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id,
            @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        return etag(jobs.close(caller, id, IfMatch.parse(ifMatch)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('RECRUITER')")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id) {
        jobs.delete(caller, id);
        return ResponseEntity.noContent().build();
    }

    private static ResponseEntity<JobDetailView> etag(JobDetailView view) {
        return ResponseEntity.ok().eTag("\"" + view.version() + "\"").body(view);
    }
}
