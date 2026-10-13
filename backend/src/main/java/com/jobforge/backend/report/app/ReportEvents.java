package com.jobforge.backend.report.app;

import com.jobforge.backend.report.domain.ModerationAction;
import com.jobforge.backend.report.domain.ReportReason;
import com.jobforge.backend.report.domain.ReportTargetType;
import java.util.UUID;

/** Payloads for ARCHITECTURE 14 events on {@code jobforge.moderation.v1}. No free text (details/reasons) is carried. */
public final class ReportEvents {

    private ReportEvents() {}

    public record ContentReported(UUID reportId, UUID reporterId, ReportTargetType targetType, UUID targetId,
            ReportReason reason) {}

    /** {@code ownerUserId} is the user notified (CONTENT_MODERATED); it never identifies the reporter. */
    public record ContentModerated(ReportTargetType targetType, UUID targetId, ModerationAction action,
            UUID ownerUserId) {}
}
