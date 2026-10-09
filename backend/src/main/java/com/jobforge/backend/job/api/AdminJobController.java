package com.jobforge.backend.job.api;

import com.jobforge.backend.job.app.JobModerationService;
import com.jobforge.backend.job.facade.JobViews.JobAdminView;
import com.jobforge.backend.shared.api.PagedResponse;
import com.jobforge.backend.shared.security.AuthenticatedUser;
import com.jobforge.backend.shared.web.QueryParams;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API_CONTRACT 12.14 - admin job moderation (ADMIN only). */
@RestController
@RequestMapping("/admin/jobs")
@PreAuthorize("hasRole('ADMIN')")
public class AdminJobController {

    public record RemoveRequest(@NotBlank @Size(max = 500) String reason) {}

    private final JobModerationService moderation;

    public AdminJobController(JobModerationService moderation) {
        this.moderation = moderation;
    }

    @GetMapping
    public PagedResponse<JobAdminView> list(HttpServletRequest request) {
        QueryParams p = new QueryParams(request, "q", "status", "companyId", "page", "size");
        int page = p.page();
        int size = p.size();
        return moderation.search(p.string("q"), p.string("status"), p.uuid("companyId"), page, size)
                .toResponse(Function.identity(), page, size);
    }

    @PostMapping("/{id}/remove")
    public JobAdminView remove(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable UUID id,
            @Valid @RequestBody RemoveRequest body) {
        return moderation.remove(admin.id(), id, body.reason());
    }

    @PostMapping("/{id}/restore")
    public JobAdminView restore(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable UUID id) {
        return moderation.restore(admin.id(), id);
    }
}
