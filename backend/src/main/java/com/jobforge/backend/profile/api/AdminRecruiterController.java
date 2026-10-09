package com.jobforge.backend.profile.api;

import com.jobforge.backend.profile.app.RecruiterApprovalRepository.Row;
import com.jobforge.backend.profile.app.RecruiterApprovalService;
import com.jobforge.backend.shared.api.PagedResponse;
import com.jobforge.backend.shared.error.ValidationFailedException;
import com.jobforge.backend.shared.security.AuthenticatedUser;
import com.jobforge.backend.shared.web.QueryParams;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/recruiters")
@PreAuthorize("hasRole('ADMIN')")
public class AdminRecruiterController {

    public record Reject(@NotBlank @Size(max = 500) String reason) {}

    private static final List<String> STATUSES = List.of("PENDING", "APPROVED", "REJECTED", "SUSPENDED");

    private final RecruiterApprovalService approvals;

    public AdminRecruiterController(RecruiterApprovalService approvals) {
        this.approvals = approvals;
    }

    @GetMapping
    public PagedResponse<Row> list(HttpServletRequest request) {
        QueryParams params = new QueryParams(request, "status", "page", "size");
        String status = params.string("status");
        if (status != null && !STATUSES.contains(status)) {
            throw ValidationFailedException.of("status", "INVALID_ENUM", "must be one of the allowed values");
        }
        int page = params.page();
        int size = params.size();
        return approvals.list(status, page, size).toResponse(Function.identity(), page, size);
    }

    public record DetailResponse(UUID userId, String email, String firstName, String lastName, String jobTitle, String phone,
            String approvalStatus, String rejectionReason, String companyName, UUID companyId, java.time.Instant createdAt) {}

    /** Admin detail view of a recruiter (decisions are still taken via approve/reject). */
    @GetMapping("/{userId}")
    public DetailResponse get(@PathVariable UUID userId) {
        var d = approvals.get(userId);
        var r = d.row();
        return new DetailResponse(r.userId(), r.email(), r.firstName(), r.lastName(), r.jobTitle(), d.phone(),
                r.approvalStatus(), r.rejectionReason(), r.companyName(), d.companyId(), r.createdAt());
    }

    @PostMapping("/{userId}/approve")
    public ResponseEntity<Void> approve(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable UUID userId) {
        approvals.decide(admin, userId, true, null);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{userId}/reject")
    public ResponseEntity<Void> reject(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable UUID userId,
            @Valid @RequestBody Reject request) {
        approvals.decide(admin, userId, false, request.reason());
        return ResponseEntity.noContent().build();
    }
}
