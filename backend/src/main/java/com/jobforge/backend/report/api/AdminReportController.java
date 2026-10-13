package com.jobforge.backend.report.api;

import com.jobforge.backend.report.app.ReportService;
import com.jobforge.backend.report.app.ReportText;
import com.jobforge.backend.report.app.ReportViews.AdminReportDetail;
import com.jobforge.backend.report.app.ReportViews.AdminReportItem;
import com.jobforge.backend.report.domain.ModerationAction;
import com.jobforge.backend.report.domain.ReportReason;
import com.jobforge.backend.report.domain.ReportStatus;
import com.jobforge.backend.report.domain.ReportTargetType;
import com.jobforge.backend.shared.api.PagedResponse;
import com.jobforge.backend.shared.error.ValidationFailedException;
import com.jobforge.backend.shared.security.AuthenticatedUser;
import com.jobforge.backend.shared.web.QueryParams;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
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

/**
 * API_CONTRACT 12.11 - report queue, detail and resolve. ADMIN only (URL rule {@code /admin/**} plus
 * {@code @PreAuthorize}); the schema has no MODERATOR role, so no other role gains access.
 */
@RestController
@RequestMapping("/admin/reports")
@PreAuthorize("hasRole('ADMIN')")
public class AdminReportController {

    /** Queue order: oldest first by default (FIFO work queue); {@code sort=createdAt,desc} for newest first. */
    private static final List<String> SORTS = List.of("createdAt,asc", "createdAt,desc");

    /** Moderation reason length after trimming (API_CONTRACT 12.11 resolve: 10-500 characters). */
    static final int REASON_MIN = 10;
    static final int REASON_MAX = 500;

    /**
     * The length bound is applied to the trimmed value in {@link #normalizeReason(String)}, not with a raw
     * {@code @Size}: whitespace padding must neither satisfy the minimum nor consume the maximum.
     */
    public record ResolveRequest(
            @NotNull ModerationAction action,
            @NotBlank String reason) {}

    private final ReportService reports;

    public AdminReportController(ReportService reports) {
        this.reports = reports;
    }

    @GetMapping
    public PagedResponse<AdminReportItem> list(HttpServletRequest request) {
        QueryParams p = new QueryParams(request, "status", "targetType", "reason", "sort", "page", "size");
        int page = p.page();
        int size = p.size();
        ReportStatus status = p.enumValue("status", ReportStatus.class);
        ReportTargetType targetType = p.enumValue("targetType", ReportTargetType.class);
        ReportReason reason = p.enumValue("reason", ReportReason.class);
        boolean newestFirst = newestFirst(p.sortValues());
        return reports.adminSearch(status, targetType, reason, newestFirst, page, size)
                .toResponse(Function.identity(), page, size);
    }

    @GetMapping("/{id}")
    public AdminReportDetail get(@PathVariable UUID id) {
        return reports.adminDetail(id);
    }

    @PostMapping("/{id}/resolve")
    public AdminReportDetail resolve(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable UUID id,
            @Valid @RequestBody ResolveRequest body) {
        return reports.resolve(admin, id, body.action(), normalizeReason(body.reason()));
    }

    /**
     * Trims the reason and enforces 10-500 characters (Unicode code points) on the trimmed value; 400 otherwise.
     * Trimming uses the same character set as the frontend's {@code String.prototype.trim} (see {@link ReportText#trim}),
     * so both sides measure the same value; {@code String.strip()} would keep a non-breaking-space padding.
     */
    static String normalizeReason(String raw) {
        String reason = raw == null ? "" : ReportText.trim(raw);
        int length = reason.codePointCount(0, reason.length());
        if (length < REASON_MIN || length > REASON_MAX) {
            throw ValidationFailedException.of("reason", "SIZE",
                    "must be between " + REASON_MIN + " and " + REASON_MAX + " characters");
        }
        return reason;
    }

    private static boolean newestFirst(List<String> sorts) {
        if (sorts.isEmpty()) {
            return false;
        }
        if (sorts.size() > 1 || !SORTS.contains(sorts.get(0))) {
            throw ValidationFailedException.of("sort", "INVALID_SORT", "must be one of " + SORTS);
        }
        return sorts.get(0).endsWith("desc");
    }
}
