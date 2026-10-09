package com.jobforge.backend.job.api;

import com.jobforge.backend.job.app.SavedJobService;
import com.jobforge.backend.job.facade.JobViews.JobSummaryView;
import com.jobforge.backend.shared.api.PagedResponse;
import com.jobforge.backend.shared.security.AuthenticatedUser;
import com.jobforge.backend.shared.web.QueryParams;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API_CONTRACT §12.5 saved jobs (JOB_SEEKER only). */
@RestController
@RequestMapping("/seekers/me/saved-jobs")
@PreAuthorize("hasRole('JOB_SEEKER')")
public class SavedJobController {

    private final SavedJobService saved;

    public SavedJobController(SavedJobService saved) {
        this.saved = saved;
    }

    @GetMapping
    public PagedResponse<JobSummaryView> list(@AuthenticationPrincipal AuthenticatedUser caller, HttpServletRequest request) {
        QueryParams q = new QueryParams(request, "page", "size");
        int page = q.page();
        int size = q.size();
        return saved.list(caller, page, size).toResponse(Function.identity(), page, size);
    }

    @PutMapping("/{jobId}")
    public ResponseEntity<Void> save(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID jobId) {
        saved.save(caller, jobId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{jobId}")
    public ResponseEntity<Void> unsave(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID jobId) {
        saved.unsave(caller, jobId);
        return ResponseEntity.noContent().build();
    }
}
