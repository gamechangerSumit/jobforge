package com.jobforge.backend.report.app;

import com.jobforge.backend.report.app.ReportRepository.ModerationRecord;
import com.jobforge.backend.report.app.ReportTargetPort.ModerationContext;
import com.jobforge.backend.report.app.ReportTargetPort.TargetSnapshot;
import com.jobforge.backend.report.app.ReportViews.AdminReportDetail;
import com.jobforge.backend.report.app.ReportViews.AdminReportItem;
import com.jobforge.backend.report.app.ReportViews.ModerationActionView;
import com.jobforge.backend.report.app.ReportViews.ReportAck;
import com.jobforge.backend.report.app.ReportViews.ReporterView;
import com.jobforge.backend.report.app.ReportViews.TargetView;
import com.jobforge.backend.report.domain.ModerationAction;
import com.jobforge.backend.report.domain.Report;
import com.jobforge.backend.report.domain.ReportReason;
import com.jobforge.backend.report.domain.ReportStatus;
import com.jobforge.backend.report.domain.ReportTargetType;
import com.jobforge.backend.report.domain.ReportTransitions;
import com.jobforge.backend.shared.api.PagedResult;
import com.jobforge.backend.shared.audit.AuditAction;
import com.jobforge.backend.shared.audit.AuditEntry;
import com.jobforge.backend.shared.audit.AuditOutcome;
import com.jobforge.backend.shared.audit.AuditService;
import com.jobforge.backend.shared.domain.UserRole;
import com.jobforge.backend.shared.domain.UserStatus;
import com.jobforge.backend.shared.error.BusinessRuleException;
import com.jobforge.backend.shared.error.ConflictException;
import com.jobforge.backend.shared.error.ErrorCode;
import com.jobforge.backend.shared.error.ResourceNotFoundException;
import com.jobforge.backend.shared.events.DomainEvent;
import com.jobforge.backend.shared.events.EventPublisher;
import com.jobforge.backend.shared.events.EventTopics;
import com.jobforge.backend.shared.security.AuthenticatedUser;
import com.jobforge.backend.user.facade.UserAccountView;
import com.jobforge.backend.user.facade.UserFacade;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reporting and moderation (API_CONTRACT 12.9 POST /reports, 12.11 /admin/reports).
 *
 * <p>Security model: any authenticated user may file a report about something they can currently see; the target
 * check answers "not found" for missing AND non-visible resources so a report cannot be used to probe hidden content.
 * Everything under the admin methods is ADMIN-only at the API edge (there is no MODERATOR role in the schema).
 */
@Service
public class ReportService {

    private static final String UNAVAILABLE = "UNAVAILABLE";

    private final ReportRepository reports;
    private final Map<ReportTargetType, ReportTargetPort> ports = new EnumMap<>(ReportTargetType.class);
    private final UserFacade users;
    private final AuditService audit;
    private final EventPublisher events;
    private final Clock clock;

    public ReportService(ReportRepository reports, List<ReportTargetPort> targetPorts, UserFacade users,
            AuditService audit, EventPublisher events, Clock clock) {
        this.reports = reports;
        targetPorts.forEach(p -> this.ports.put(p.type(), p));
        this.users = users;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ filing

    @Transactional
    public ReportAck create(AuthenticatedUser reporter, ReportTargetType targetType, UUID targetId,
            ReportReason reason, String details) {
        ReportTargetPort port = ports.get(targetType);
        if (port == null) {
            throw new BusinessRuleException("Reporting " + targetType + " content is not available yet.");
        }
        // Missing and not-visible are indistinguishable on purpose (no probing of hidden/private resources).
        TargetSnapshot target = port.snapshot(targetId).filter(TargetSnapshot::reportable)
                .orElseThrow(() -> new ResourceNotFoundException("The reported item was not found."));
        if (reporter.id().equals(target.ownerUserId())) {
            throw new BusinessRuleException("You cannot report your own content or account.");
        }
        String cleanDetails = ReportText.normalizeDetails(details);
        Instant now = clock.instant();
        Report report = new Report(UUID.randomUUID(), reporter.id(), targetType, targetId, reason, cleanDetails,
                ReportStatus.OPEN, now, now);
        if (!reports.insertIfNoActive(report)) {
            throw new ConflictException(ErrorCode.CONFLICT, "You already have an open report for this item.");
        }
        audit.record(new AuditEntry(AuditAction.REPORT_FILED, "Report", report.id(), reporter.id(), reporter.role(),
                AuditOutcome.SUCCESS, null, Map.of("status", ReportStatus.OPEN.name()),
                Map.of("targetType", targetType.name(), "targetId", targetId.toString(), "reason", reason.name())));
        events.publish(new DomainEvent(EventTopics.MODERATION, "ContentReported", "Report", report.id(),
                reporter.id(), reporter.role(),
                new ReportEvents.ContentReported(report.id(), reporter.id(), targetType, targetId, reason)));
        return new ReportAck(report.id(), targetType, targetId, reason, report.status(), now);
    }

    // ------------------------------------------------------------------ admin queue

    @Transactional(readOnly = true)
    public PagedResult<AdminReportItem> adminSearch(ReportStatus status, ReportTargetType targetType,
            ReportReason reason, boolean newestFirst, int page, int size) {
        PagedResult<Report> result = reports.search(status, targetType, reason, newestFirst, page, size);
        return new PagedResult<>(result.items().stream().map(ReportService::toItem).toList(), result.total());
    }

    @Transactional(readOnly = true)
    public AdminReportDetail adminDetail(UUID reportId) {
        Report report = reports.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found."));
        return toDetail(report);
    }

    // ------------------------------------------------------------------ moderation decision

    @Transactional
    public AdminReportDetail resolve(AuthenticatedUser moderator, UUID reportId, ModerationAction action,
            String reason) {
        Report report = reports.findByIdForUpdate(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found."));
        ReportTransitions.requireResolvable(report.status());
        ReportStatus next = ReportTransitions.outcomeOf(action);

        UUID ownerUserId = null;
        if (action != ModerationAction.DISMISS) {
            ReportTargetPort port = ports.get(report.targetType());
            TargetSnapshot target = port == null ? null : port.snapshot(report.targetId()).orElse(null);
            if (target == null) {
                throw new BusinessRuleException("The reported item no longer exists. Dismiss the report instead.");
            }
            ownerUserId = target.ownerUserId();
            if (action == ModerationAction.WARN_USER) {
                if (ownerUserId == null) {
                    throw new BusinessRuleException("The user responsible for this item cannot be determined.");
                }
            } else {
                port.apply(action, new ModerationContext(moderator, report.targetId(), reason));
            }
        }

        Instant now = clock.instant();
        reports.insertAction(new ModerationRecord(UUID.randomUUID(), report.id(), moderator.id(), action,
                report.targetType(), report.targetId(), reason, now));
        if (!reports.markResolved(report.id(), next, now)) {
            throw new ConflictException(ErrorCode.INVALID_STATE_TRANSITION,
                    "The report was resolved concurrently.");
        }

        audit.record(new AuditEntry(AuditAction.REPORT_RESOLVED, "Report", report.id(), moderator.id(),
                UserRole.ADMIN, AuditOutcome.SUCCESS, Map.of("status", report.status().name()),
                Map.of("status", next.name()),
                Map.of("action", action.name(), "targetType", report.targetType().name(),
                        "targetId", report.targetId().toString(), "reason", reason)));
        if (action != ModerationAction.DISMISS) {
            audit.record(new AuditEntry(AuditAction.CONTENT_MODERATED, report.targetType().auditEntityType(),
                    report.targetId(), moderator.id(), UserRole.ADMIN, AuditOutcome.SUCCESS, null, null,
                    Map.of("action", action.name(), "reportId", report.id().toString(), "reason", reason)));
            events.publish(new DomainEvent(EventTopics.MODERATION, "ContentModerated",
                    report.targetType().auditEntityType(), report.targetId(), moderator.id(), UserRole.ADMIN,
                    new ReportEvents.ContentModerated(report.targetType(), report.targetId(), action, ownerUserId)));
        }
        return toDetail(reports.findById(report.id()).orElseThrow());
    }

    // ------------------------------------------------------------------ mapping

    private static AdminReportItem toItem(Report r) {
        return new AdminReportItem(r.id(), r.targetType(), r.targetId(), r.reason(), r.status(), r.reporterId(),
                r.createdAt(), r.updatedAt());
    }

    private AdminReportDetail toDetail(Report r) {
        List<ModerationActionView> actions = reports.actionsOf(r.id()).stream()
                .map(a -> new ModerationActionView(a.id(), a.action(), a.moderatorId(), a.reason(), a.createdAt()))
                .toList();
        return new AdminReportDetail(r.id(), r.targetType(), r.targetId(), r.reason(), r.details(), r.status(),
                reporterView(r.reporterId()), targetView(r), actions, r.createdAt(), r.updatedAt());
    }

    private ReporterView reporterView(UUID reporterId) {
        Optional<UserAccountView> user = users.findById(reporterId);
        String name = user.filter(u -> u.status() != UserStatus.DELETED)
                .map(u -> (nullToEmpty(u.firstName()) + " " + nullToEmpty(u.lastName())).strip())
                .filter(n -> !n.isBlank()).orElse("Deleted user");
        return new ReporterView(reporterId, name);
    }

    private TargetView targetView(Report r) {
        ReportTargetPort port = ports.get(r.targetType());
        Optional<TargetSnapshot> snapshot = port == null ? Optional.empty() : port.snapshot(r.targetId());
        return snapshot.map(s -> new TargetView(r.targetType(), r.targetId(), s.label(), s.status(), true))
                .orElseGet(() -> new TargetView(r.targetType(), r.targetId(), null, UNAVAILABLE, false));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
