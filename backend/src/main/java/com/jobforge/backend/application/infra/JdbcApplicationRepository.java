package com.jobforge.backend.application.infra;

import com.jobforge.backend.application.app.ApplicationRepository;
import com.jobforge.backend.application.domain.Application;
import com.jobforge.backend.application.domain.ApplicationStatus;
import com.jobforge.backend.application.domain.StatusHistoryEntry;
import com.jobforge.backend.shared.api.PagedResult;
import com.jobforge.backend.shared.persistence.Db;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcApplicationRepository implements ApplicationRepository {

    private static final String COLUMNS = """
            id, job_id, seeker_user_id, resume_id, cover_letter, status, profile_snapshot::text AS profile_snapshot,
            rating, applied_at, status_updated_at, withdrawn_at, version, created_at, updated_at
            """;

    private final JdbcClient jdbc;

    public JdbcApplicationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean insertIfAbsent(Application a, Instant now) {
        return jdbc.sql("""
                INSERT INTO core.applications
                  (id, job_id, seeker_user_id, resume_id, cover_letter, status, profile_snapshot, applied_at,
                   status_updated_at, version, created_at, updated_at)
                VALUES
                  (:id, :job, :seeker, :resume, :cover, 'SUBMITTED', CAST(:snapshot AS jsonb), :now, :now, 0, :now, :now)
                ON CONFLICT (job_id, seeker_user_id) DO NOTHING
                """)
                .param("id", a.id()).param("job", a.jobId()).param("seeker", a.seekerUserId())
                .param("resume", a.resumeId()).param("cover", a.coverLetter()).param("snapshot", a.profileSnapshot())
                .param("now", Db.ts(now)).update() == 1;
    }

    @Override
    public Optional<Application> findById(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM core.applications WHERE id = :id").param("id", id)
                .query(JdbcApplicationRepository::map).optional();
    }

    @Override
    public Optional<Application> findByJobAndSeeker(UUID jobId, UUID seekerUserId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM core.applications WHERE job_id = :job AND seeker_user_id = :seeker")
                .param("job", jobId).param("seeker", seekerUserId).query(JdbcApplicationRepository::map).optional();
    }

    @Override
    public boolean updateStatus(UUID id, ApplicationStatus to, long expectedVersion, Instant now, boolean withdrawn) {
        return jdbc.sql("""
                UPDATE core.applications
                   SET status = :status, status_updated_at = :now,
                       withdrawn_at = CASE WHEN :withdrawn THEN :now ELSE withdrawn_at END,
                       version = version + 1, updated_at = :now
                 WHERE id = :id AND version = :ver
                """).param("status", to.name()).param("now", Db.ts(now)).param("withdrawn", withdrawn)
                .param("id", id).param("ver", expectedVersion).update() == 1;
    }

    @Override
    public void updateRating(UUID id, int rating, Instant now) {
        jdbc.sql("UPDATE core.applications SET rating = :rating, version = version + 1, updated_at = :now WHERE id = :id")
                .param("rating", rating).param("now", Db.ts(now)).param("id", id).update();
    }

    @Override
    public void addHistory(StatusHistoryEntry e) {
        jdbc.sql("""
                INSERT INTO core.application_status_history (id, application_id, from_status, to_status, changed_by, reason, created_at)
                VALUES (:id, :app, :from, :to, :by, :reason, :at)
                """)
                .param("id", e.id()).param("app", e.applicationId()).param("from", e.from() == null ? null : e.from().name())
                .param("to", e.to().name()).param("by", e.changedBy()).param("reason", e.reason())
                .param("at", Db.ts(e.createdAt())).update();
    }

    @Override
    public List<StatusHistoryEntry> history(UUID applicationId) {
        return jdbc.sql("""
                SELECT id, application_id, from_status, to_status, changed_by, reason, created_at
                  FROM core.application_status_history WHERE application_id = :app ORDER BY created_at, id
                """).param("app", applicationId)
                .query((rs, n) -> new StatusHistoryEntry(Db.uuid(rs, "id"), Db.uuid(rs, "application_id"),
                        rs.getString("from_status") == null ? null : ApplicationStatus.valueOf(rs.getString("from_status")),
                        ApplicationStatus.valueOf(rs.getString("to_status")), Db.uuid(rs, "changed_by"),
                        rs.getString("reason"), Db.instant(rs, "created_at")))
                .list();
    }

    @Override
    public PagedResult<Application> findBySeeker(UUID seekerUserId, ApplicationStatus status, String orderBy, int page, int size) {
        Map<String, Object> params = new HashMap<>();
        StringBuilder where = new StringBuilder("seeker_user_id = :seeker");
        params.put("seeker", seekerUserId);
        if (status != null) {
            where.append(" AND status = :status");
            params.put("status", status.name());
        }
        return page(where.toString(), params, orderBy, page, size);
    }

    @Override
    public PagedResult<Application> findByJobs(Collection<UUID> jobIds, ApplicationStatus status, Collection<UUID> seekerIds,
            String orderBy, int page, int size) {
        if (jobIds.isEmpty() || (seekerIds != null && seekerIds.isEmpty())) {
            return PagedResult.empty();
        }
        Map<String, Object> params = new HashMap<>();
        StringBuilder where = new StringBuilder("job_id IN (:jobs)");
        params.put("jobs", jobIds);
        if (status != null) {
            where.append(" AND status = :status");
            params.put("status", status.name());
        }
        if (seekerIds != null) {
            where.append(" AND seeker_user_id IN (:seekers)");
            params.put("seekers", seekerIds);
        }
        return page(where.toString(), params, orderBy, page, size);
    }

    @Override
    public long countByJob(UUID jobId) {
        return jdbc.sql("SELECT count(*) FROM core.applications WHERE job_id = :job").param("job", jobId)
                .query(Long.class).single();
    }

    @Override
    public Set<UUID> appliedJobIds(UUID seekerUserId, Collection<UUID> jobIds) {
        if (jobIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(jdbc.sql("SELECT job_id FROM core.applications WHERE seeker_user_id = :seeker AND job_id IN (:jobs)")
                .param("seeker", seekerUserId).param("jobs", jobIds).query((rs, n) -> Db.uuid(rs, "job_id")).list());
    }

    @Override
    public boolean existsActive(UUID seekerUserId, Collection<UUID> jobIds) {
        if (jobIds.isEmpty()) {
            return false;
        }
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM core.applications WHERE seeker_user_id = :seeker AND status <> 'WITHDRAWN' AND job_id IN (:jobs))")
                .param("seeker", seekerUserId).param("jobs", jobIds).query(Boolean.class).single();
    }

    @Override
    public List<Application> findAllByIds(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("SELECT " + COLUMNS + " FROM core.applications WHERE id IN (:ids)").param("ids", ids)
                .query(JdbcApplicationRepository::map).list();
    }

    @Override
    public List<UUID> idsByJobs(Collection<UUID> jobIds) {
        if (jobIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("SELECT id FROM core.applications WHERE job_id IN (:jobs)").param("jobs", jobIds)
                .query((rs, n) -> Db.uuid(rs, "id")).list();
    }

    @Override
    public List<UUID> idsBySeeker(UUID seekerUserId) {
        return jdbc.sql("SELECT id FROM core.applications WHERE seeker_user_id = :seeker").param("seeker", seekerUserId)
                .query((rs, n) -> Db.uuid(rs, "id")).list();
    }

    private PagedResult<Application> page(String where, Map<String, Object> params, String orderBy, int page, int size) {
        Long total = jdbc.sql("SELECT count(*) FROM core.applications WHERE " + where).params(params).query(Long.class).single();
        Map<String, Object> pageParams = new HashMap<>(params);
        pageParams.put("limit", size);
        pageParams.put("offset", (long) page * size);
        List<Application> items = jdbc.sql("SELECT " + COLUMNS + " FROM core.applications WHERE " + where + orderBy
                        + " LIMIT :limit OFFSET :offset")
                .params(pageParams).query(JdbcApplicationRepository::map).list();
        return new PagedResult<>(items, total);
    }

    private static Application map(ResultSet rs, int row) throws SQLException {
        Object rating = rs.getObject("rating");
        return new Application(Db.uuid(rs, "id"), Db.uuid(rs, "job_id"), Db.uuid(rs, "seeker_user_id"),
                Db.uuid(rs, "resume_id"), rs.getString("cover_letter"), ApplicationStatus.valueOf(rs.getString("status")),
                rs.getString("profile_snapshot"), rating == null ? null : ((Number) rating).intValue(),
                Db.instant(rs, "applied_at"), Db.instant(rs, "status_updated_at"), Db.instant(rs, "withdrawn_at"),
                rs.getLong("version"), Db.instant(rs, "created_at"), Db.instant(rs, "updated_at"));
    }
}
