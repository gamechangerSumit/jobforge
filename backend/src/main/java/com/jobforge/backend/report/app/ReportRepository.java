package com.jobforge.backend.report.app;

import com.jobforge.backend.report.domain.ModerationAction;
import com.jobforge.backend.report.domain.Report;
import com.jobforge.backend.report.domain.ReportReason;
import com.jobforge.backend.report.domain.ReportStatus;
import com.jobforge.backend.report.domain.ReportTargetType;
import com.jobforge.backend.shared.api.PagedResult;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence port of the report module (core.reports, core.moderation_actions). */
public interface ReportRepository {

    record ModerationRecord(UUID id, UUID reportId, UUID moderatorId, ModerationAction action,
            ReportTargetType targetType, UUID targetId, String reason, Instant createdAt) {}

    /** @return false when the reporter already has an OPEN/REVIEWING report for the same target (no row inserted). */
    boolean insertIfNoActive(Report report);

    Optional<Report> findById(UUID id);

    /** Row lock for the resolve transaction. */
    Optional<Report> findByIdForUpdate(UUID id);

    PagedResult<Report> search(ReportStatus status, ReportTargetType targetType, ReportReason reason,
            boolean newestFirst, int page, int size);

    /** Guarded update: only OPEN/REVIEWING rows change. */
    boolean markResolved(UUID id, ReportStatus newStatus, Instant now);

    void insertAction(ModerationRecord record);

    List<ModerationRecord> actionsOf(UUID reportId);
}
