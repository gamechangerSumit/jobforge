package com.jobforge.backend.report.infra;

import com.jobforge.backend.report.app.ReportRepository;
import com.jobforge.backend.report.domain.ModerationAction;
import com.jobforge.backend.report.domain.Report;
import com.jobforge.backend.report.domain.ReportReason;
import com.jobforge.backend.report.domain.ReportStatus;
import com.jobforge.backend.report.domain.ReportTargetType;
import com.jobforge.backend.shared.api.PagedResult;
import com.jobforge.backend.shared.persistence.Db;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Spring JDBC implementation over core.reports and core.moderation_actions (DATABASE_SCHEMA 4.6). */
@Repository
public class JdbcReportRepository implements ReportRepository {

    private static final String COLUMNS =
            "id, reporter_id, target_type, target_id, reason, details, status, created_at, updated_at";

    private final JdbcClient jdbc;

    public JdbcReportRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean insertIfNoActive(Report r) {
        // The conflict target mirrors the partial unique index uq_reports_active.
        int rows = jdbc.sql("""
                INSERT INTO core.reports (id, reporter_id, target_type, target_id, reason, details, status, created_at, updated_at)
                VALUES (:id, :reporter, :type, :target, :reason, :details, :status, :now, :now)
                ON CONFLICT (reporter_id, target_type, target_id) WHERE status IN ('OPEN', 'REVIEWING') DO NOTHING
                """)
                .param("id", r.id()).param("reporter", r.reporterId()).param("type", r.targetType().name())
                .param("target", r.targetId()).param("reason", r.reason().name()).param("details", r.details())
                .param("status", r.status().name()).param("now", Db.ts(r.createdAt())).update();
        return rows == 1;
    }

    @Override
    public Optional<Report> findById(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM core.reports WHERE id = :id").param("id", id)
                .query(JdbcReportRepository::mapReport).optional();
    }

    @Override
    public Optional<Report> findByIdForUpdate(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM core.reports WHERE id = :id FOR UPDATE").param("id", id)
                .query(JdbcReportRepository::mapReport).optional();
    }

    @Override
    public PagedResult<Report> search(ReportStatus status, ReportTargetType targetType, ReportReason reason,
            boolean newestFirst, int page, int size) {
        String where = """
                 WHERE (CAST(:status AS varchar) IS NULL OR status = :status)
                   AND (CAST(:type AS varchar) IS NULL OR target_type = :type)
                   AND (CAST(:reason AS varchar) IS NULL OR reason = :reason)
                """;
        String statusValue = status == null ? null : status.name();
        String typeValue = targetType == null ? null : targetType.name();
        String reasonValue = reason == null ? null : reason.name();
        Long total = jdbc.sql("SELECT count(*) FROM core.reports" + where)
                .param("status", statusValue).param("type", typeValue).param("reason", reasonValue)
                .query(Long.class).single();
        // Fixed, whitelisted ORDER BY (never built from request text); id breaks created_at ties deterministically.
        String order = newestFirst ? " ORDER BY created_at DESC, id DESC" : " ORDER BY created_at ASC, id ASC";
        List<Report> items = jdbc.sql("SELECT " + COLUMNS + " FROM core.reports" + where + order
                        + " LIMIT :limit OFFSET :offset")
                .param("status", statusValue).param("type", typeValue).param("reason", reasonValue)
                .param("limit", size).param("offset", (long) page * size)
                .query(JdbcReportRepository::mapReport).list();
        return new PagedResult<>(items, total);
    }

    @Override
    public boolean markResolved(UUID id, ReportStatus newStatus, Instant now) {
        return jdbc.sql("UPDATE core.reports SET status = :status, updated_at = :now "
                        + "WHERE id = :id AND status IN ('OPEN', 'REVIEWING')")
                .param("status", newStatus.name()).param("now", Db.ts(now)).param("id", id).update() == 1;
    }

    @Override
    public void insertAction(ModerationRecord m) {
        jdbc.sql("""
                INSERT INTO core.moderation_actions (id, report_id, moderator_id, action, target_type, target_id, reason, created_at)
                VALUES (:id, :report, :moderator, :action, :type, :target, :reason, :now)
                """)
                .param("id", m.id()).param("report", m.reportId()).param("moderator", m.moderatorId())
                .param("action", m.action().name()).param("type", m.targetType().name())
                .param("target", m.targetId()).param("reason", m.reason()).param("now", Db.ts(m.createdAt()))
                .update();
    }

    @Override
    public List<ModerationRecord> actionsOf(UUID reportId) {
        return jdbc.sql("SELECT id, report_id, moderator_id, action, target_type, target_id, reason, created_at "
                        + "FROM core.moderation_actions WHERE report_id = :report ORDER BY created_at, id")
                .param("report", reportId).query((rs, n) -> new ModerationRecord(Db.uuid(rs, "id"),
                        Db.uuid(rs, "report_id"), Db.uuid(rs, "moderator_id"),
                        ModerationAction.valueOf(rs.getString("action")),
                        ReportTargetType.valueOf(rs.getString("target_type")), Db.uuid(rs, "target_id"),
                        rs.getString("reason"), Db.instant(rs, "created_at"))).list();
    }

    private static Report mapReport(ResultSet rs, int rowNum) throws SQLException {
        return new Report(Db.uuid(rs, "id"), Db.uuid(rs, "reporter_id"),
                ReportTargetType.valueOf(rs.getString("target_type")), Db.uuid(rs, "target_id"),
                ReportReason.valueOf(rs.getString("reason")), rs.getString("details"),
                ReportStatus.valueOf(rs.getString("status")), Db.instant(rs, "created_at"),
                Db.instant(rs, "updated_at"));
    }
}
