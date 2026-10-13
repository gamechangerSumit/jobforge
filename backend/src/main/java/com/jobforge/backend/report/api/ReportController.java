package com.jobforge.backend.report.api;

import com.jobforge.backend.report.app.ReportService;
import com.jobforge.backend.report.app.ReportViews.ReportAck;
import com.jobforge.backend.report.domain.ReportReason;
import com.jobforge.backend.report.domain.ReportTargetType;
import com.jobforge.backend.shared.config.ApiPaths;
import com.jobforge.backend.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API_CONTRACT 12.9 - {@code POST /reports} (any authenticated role). Covered by the "anyRequest authenticated" rule;
 * no URL rule is added. The contract defines no endpoint for a reporter to list or read reports, so none exists.
 * The 201 response carries a {@code Location} header (API_CONTRACT §4) pointing at the report's only resource URI,
 * {@code /admin/reports/{id}}, which is ADMIN-readable (a reporter following it gets 403).
 */
@RestController
@RequestMapping("/reports")
public class ReportController {

    public record CreateReportRequest(
            @NotNull ReportTargetType targetType,
            @NotNull UUID targetId,
            @NotNull ReportReason reason,
            @Size(max = 1000) String details) {}

    private final ReportService reports;

    public ReportController(ReportService reports) {
        this.reports = reports;
    }

    @PostMapping
    public ResponseEntity<ReportAck> create(@AuthenticationPrincipal AuthenticatedUser caller,
            @Valid @RequestBody CreateReportRequest body) {
        ReportAck ack = reports.create(caller, body.targetType(), body.targetId(), body.reason(), body.details());
        return ResponseEntity.created(URI.create(ApiPaths.BASE + "/admin/reports/" + ack.id())).body(ack);
    }
}
