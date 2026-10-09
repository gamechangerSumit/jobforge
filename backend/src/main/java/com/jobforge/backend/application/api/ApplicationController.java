package com.jobforge.backend.application.api;

import com.jobforge.backend.application.app.ApplicationCommands;
import com.jobforge.backend.application.app.ApplicationService;
import com.jobforge.backend.application.app.ApplicationViews.ApplicationView;
import com.jobforge.backend.application.app.ApplyUseCase;
import com.jobforge.backend.application.domain.ApplicationStatus;
import com.jobforge.backend.shared.api.PagedResponse;
import com.jobforge.backend.shared.api.SortSpec;
import com.jobforge.backend.shared.config.ApiPaths;
import com.jobforge.backend.shared.error.ValidationFailedException;
import com.jobforge.backend.shared.security.AuthenticatedUser;
import com.jobforge.backend.shared.web.IfMatch;
import com.jobforge.backend.shared.web.QueryParams;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** API_CONTRACT §12.6 applications (JS-6 seeker side, RC-3 recruiter side). */
@RestController
public class ApplicationController {

    private static final Map<String, String> SEEKER_SORT = Map.of("appliedAt", "applied_at");
    private static final Map<String, String> JOB_SORT = Map.of("appliedAt", "applied_at", "rating", "rating");
    private static final String DEFAULT_ORDER = "applied_at DESC";
    private static final String TIE = "id DESC";

    private final ApplicationService applications;
    private final ApplyUseCase applyUseCase;

    public ApplicationController(ApplicationService applications, ApplyUseCase applyUseCase) {
        this.applications = applications;
        this.applyUseCase = applyUseCase;
    }

    // ---------------- seeker

    @PostMapping("/jobs/{jobId}/applications")
    @PreAuthorize("hasRole('JOB_SEEKER')")
    public ResponseEntity<ApplicationView> apply(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID jobId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ApplicationRequests.Apply request) {
        String key = requireIdempotencyKey(idempotencyKey);
        ApplyUseCase.Result result = applyUseCase.apply(caller, jobId,
                new ApplicationCommands.Apply(request.resumeId(), request.coverLetter()), key);
        ResponseEntity.BodyBuilder builder = ResponseEntity.created(URI.create(ApiPaths.BASE + "/applications/" + result.view().id()))
                .eTag("\"" + result.view().version() + "\"");
        if (result.replayed()) {
            builder.header("Idempotency-Replayed", "true");
        }
        return builder.body(result.view());
    }

    @GetMapping("/seekers/me/applications")
    @PreAuthorize("hasRole('JOB_SEEKER')")
    public PagedResponse<ApplicationView> mine(@AuthenticationPrincipal AuthenticatedUser caller, HttpServletRequest http) {
        QueryParams q = new QueryParams(http, "status", "page", "size", "sort");
        int page = q.page();
        int size = q.size();
        String order = SortSpec.orderBy(q.sortValues(), SEEKER_SORT, DEFAULT_ORDER, TIE);
        return applications.listForSeeker(caller, q.enumValue("status", ApplicationStatus.class), order, page, size)
                .toResponse(Function.identity(), page, size);
    }

    @PostMapping("/applications/{id}/withdraw")
    @PreAuthorize("hasRole('JOB_SEEKER')")
    public ResponseEntity<ApplicationView> withdraw(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id) {
        return etag(applications.withdraw(caller, id));
    }

    // ---------------- shared

    @GetMapping("/applications/{id}")
    @PreAuthorize("hasAnyRole('JOB_SEEKER','RECRUITER','ADMIN')")
    public ResponseEntity<ApplicationView> get(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id) {
        return etag(applications.get(caller, id));
    }

    // ---------------- recruiter / admin

    @GetMapping("/jobs/{jobId}/applications")
    @PreAuthorize("hasAnyRole('RECRUITER','ADMIN')")
    public PagedResponse<ApplicationView> forJob(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID jobId,
            HttpServletRequest http) {
        QueryParams q = new QueryParams(http, "status", "q", "page", "size", "sort");
        int page = q.page();
        int size = q.size();
        String order = SortSpec.orderBy(q.sortValues(), JOB_SORT, DEFAULT_ORDER, TIE);
        return applications.listForJob(caller, jobId, q.enumValue("status", ApplicationStatus.class), q.string("q"), order, page, size)
                .toResponse(Function.identity(), page, size);
    }

    @GetMapping("/recruiters/me/applications")
    @PreAuthorize("hasRole('RECRUITER')")
    public PagedResponse<ApplicationView> pipeline(@AuthenticationPrincipal AuthenticatedUser caller, HttpServletRequest http) {
        QueryParams q = new QueryParams(http, "status", "jobId", "page", "size");
        int page = q.page();
        int size = q.size();
        String order = SortSpec.orderBy(null, Map.of(), DEFAULT_ORDER, TIE);
        return applications.listForRecruiter(caller, q.enumValue("status", ApplicationStatus.class), q.uuid("jobId"), order, page, size)
                .toResponse(Function.identity(), page, size);
    }

    @PatchMapping("/applications/{id}/status")
    @PreAuthorize("hasRole('RECRUITER')")
    public ResponseEntity<ApplicationView> changeStatus(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @Valid @RequestBody ApplicationRequests.StatusChange request) {
        return etag(applications.changeStatus(caller, id, request.status(), request.reason(), IfMatch.parse(ifMatch)));
    }

    @PutMapping("/applications/{id}/rating")
    @PreAuthorize("hasRole('RECRUITER')")
    public ResponseEntity<ApplicationView> rate(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id,
            @Valid @RequestBody ApplicationRequests.Rating request) {
        return etag(applications.rate(caller, id, request.rating()));
    }

    // ---------------- helpers

    private static String requireIdempotencyKey(String header) {
        if (header == null || header.isBlank()) {
            throw ValidationFailedException.of("Idempotency-Key", "NOT_BLANK", "header is required");
        }
        try {
            return UUID.fromString(header.trim()).toString();
        } catch (IllegalArgumentException e) {
            throw ValidationFailedException.of("Idempotency-Key", "PATTERN", "must be a UUID");
        }
    }

    private static ResponseEntity<ApplicationView> etag(ApplicationView view) {
        return ResponseEntity.ok().eTag("\"" + view.version() + "\"").body(view);
    }
}
