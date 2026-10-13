package com.jobforge.backend.report.app;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.jobforge.backend.report.domain.ModerationAction;
import com.jobforge.backend.report.domain.ReportReason;
import com.jobforge.backend.report.domain.ReportStatus;
import com.jobforge.backend.report.domain.ReportTargetType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Response shapes of the report module. The reporter-facing acknowledgement never contains moderation data. */
public final class ReportViews {

    private ReportViews() {}

    /** Returned to the reporter after POST /reports: only what they submitted. */
    public record ReportAck(UUID id, ReportTargetType targetType, UUID targetId, ReportReason reason,
            ReportStatus status, Instant createdAt) {}

    /** Queue row for admins; deliberately has no reporter free text. */
    public record AdminReportItem(UUID id, ReportTargetType targetType, UUID targetId, ReportReason reason,
            ReportStatus status, UUID reporterId, Instant createdAt, Instant updatedAt) {}

    public record ReporterView(UUID id, String displayName) {}

    /** {@code available=false}: the resource no longer exists; {@code label} is then omitted. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TargetView(ReportTargetType type, UUID id, String label, String status, boolean available) {}

    public record ModerationActionView(UUID id, ModerationAction action, UUID moderatorId, String reason,
            Instant createdAt) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AdminReportDetail(UUID id, ReportTargetType targetType, UUID targetId, ReportReason reason,
            String details, ReportStatus status, ReporterView reporter, TargetView target,
            List<ModerationActionView> actions, Instant createdAt, Instant updatedAt) {}
}
