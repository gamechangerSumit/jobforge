package com.jobforge.backend.interview.infra;

import com.jobforge.backend.interview.app.InterviewRepository;
import com.jobforge.backend.interview.domain.Interview;
import com.jobforge.backend.interview.domain.InterviewResponse;
import com.jobforge.backend.interview.domain.InterviewStatus;
import com.jobforge.backend.interview.domain.InterviewType;
import com.jobforge.backend.shared.api.PagedResult;
import com.jobforge.backend.shared.persistence.Db;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** core.interviews (DATABASE_SCHEMA §4.5). The only class that touches the table. */
@Repository
public class JdbcInterviewRepository implements InterviewRepository {

    private static final String COLUMNS = """
            id, application_id, scheduled_by, type, scheduled_at, duration_minutes, timezone, location_or_link, status,
            seeker_response, seeker_response_note, internal_notes, cancelled_reason, created_at, updated_at
            """;

    private final JdbcClient jdbc;

    public JdbcInterviewRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(Interview i, Instant now) {
        jdbc.sql("""
                INSERT INTO core.interviews
                  (id, application_id, scheduled_by, type, scheduled_at, duration_minutes, timezone, location_or_link, status,
                   seeker_response, seeker_response_note, internal_notes, cancelled_reason, created_at, updated_at)
                VALUES
                  (:id, :app, :by, :type, :at, :duration, :tz, :loc, :status, :response, :responseNote, :notes, :cancelled,
                   :now, :now)
                """)
                .param("id", i.id()).param("app", i.applicationId()).param("by", i.scheduledBy())
                .param("type", i.type().name()).param("at", Db.ts(i.scheduledAt())).param("duration", i.durationMinutes())
                .param("tz", i.timezone()).param("loc", i.locationOrLink()).param("status", i.status().name())
                .param("response", i.seekerResponse().name()).param("responseNote", i.seekerResponseNote())
                .param("notes", i.internalNotes()).param("cancelled", i.cancelledReason()).param("now", Db.ts(now)).update();
    }

    @Override
    public Optional<Interview> findById(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM core.interviews WHERE id = :id").param("id", id)
                .query(JdbcInterviewRepository::map).optional();
    }

    @Override
    public boolean update(Interview n, InterviewStatus expectedStatus, Instant now) {
        return jdbc.sql("""
                UPDATE core.interviews
                   SET type = :type, scheduled_at = :at, duration_minutes = :duration, timezone = :tz,
                       location_or_link = :loc, status = :status, seeker_response = :response,
                       seeker_response_note = :responseNote, internal_notes = :notes, cancelled_reason = :cancelled,
                       updated_at = :now
                 WHERE id = :id AND status = :expected AND updated_at = :previousUpdatedAt
                """)
                .param("type", n.type().name()).param("at", Db.ts(n.scheduledAt())).param("duration", n.durationMinutes())
                .param("tz", n.timezone()).param("loc", n.locationOrLink()).param("status", n.status().name())
                .param("response", n.seekerResponse().name()).param("responseNote", n.seekerResponseNote())
                .param("notes", n.internalNotes()).param("cancelled", n.cancelledReason()).param("now", Db.ts(now))
                .param("id", n.id()).param("expected", expectedStatus.name()).param("previousUpdatedAt", Db.ts(n.updatedAt()))
                .update() == 1;
    }

    @Override
    public List<Interview> findOpenByApplication(UUID applicationId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM core.interviews WHERE application_id = :app"
                        + " AND status IN ('SCHEDULED', 'CONFIRMED', 'DECLINED') ORDER BY scheduled_at, id")
                .param("app", applicationId).query(JdbcInterviewRepository::map).list();
    }

    @Override
    public boolean existsActiveOverlap(UUID applicationId, Instant start, Instant end, UUID excludeInterviewId) {
        // Serialises "check, then insert/update" per application until the surrounding transaction ends, so two
        // simultaneous requests cannot both pass the check (the table has no exclusion constraint and the schema is fixed).
        jdbc.sql("SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtextextended(CAST(:app AS text), 0))) AS l")
                .param("app", applicationId).query(Integer.class).single();
        String exclude = excludeInterviewId == null ? "" : " AND id <> :exclude";
        var statement = jdbc.sql("""
                SELECT EXISTS (
                  SELECT 1 FROM core.interviews
                   WHERE application_id = :app AND status IN ('SCHEDULED', 'CONFIRMED')
                     AND scheduled_at < :end
                     AND scheduled_at + (duration_minutes * INTERVAL '1 minute') > :start
                """ + exclude + ")")
                .param("app", applicationId).param("start", Db.ts(start)).param("end", Db.ts(end));
        if (excludeInterviewId != null) {
            statement = statement.param("exclude", excludeInterviewId);
        }
        return statement.query(Boolean.class).single();
    }

    @Override
    public PagedResult<Interview> search(Collection<UUID> applicationIds, InterviewStatus status, Instant from, Instant to,
            int page, int size) {
        if (applicationIds.isEmpty()) {
            return PagedResult.empty();
        }
        Map<String, Object> params = new HashMap<>();
        StringBuilder where = new StringBuilder("application_id IN (:apps)");
        params.put("apps", applicationIds);
        if (status != null) {
            where.append(" AND status = :status");
            params.put("status", status.name());
        }
        if (from != null) {
            where.append(" AND scheduled_at >= :from");
            params.put("from", Db.ts(from));
        }
        if (to != null) {
            where.append(" AND scheduled_at <= :to");
            params.put("to", Db.ts(to));
        }
        Long total = jdbc.sql("SELECT count(*) FROM core.interviews WHERE " + where).params(params).query(Long.class).single();
        Map<String, Object> pageParams = new HashMap<>(params);
        pageParams.put("limit", size);
        pageParams.put("offset", (long) page * size);
        List<Interview> items = jdbc.sql("SELECT " + COLUMNS + " FROM core.interviews WHERE " + where
                        + " ORDER BY scheduled_at ASC, id ASC LIMIT :limit OFFSET :offset")
                .params(pageParams).query(JdbcInterviewRepository::map).list();
        return new PagedResult<>(items, total);
    }

    private static Interview map(ResultSet rs, int row) throws SQLException {
        return new Interview(Db.uuid(rs, "id"), Db.uuid(rs, "application_id"), Db.uuid(rs, "scheduled_by"),
                InterviewType.valueOf(rs.getString("type")), Db.instant(rs, "scheduled_at"), rs.getInt("duration_minutes"),
                rs.getString("timezone"), rs.getString("location_or_link"), InterviewStatus.valueOf(rs.getString("status")),
                InterviewResponse.valueOf(rs.getString("seeker_response")), rs.getString("seeker_response_note"),
                rs.getString("internal_notes"), rs.getString("cancelled_reason"), Db.instant(rs, "created_at"),
                Db.instant(rs, "updated_at"));
    }
}
