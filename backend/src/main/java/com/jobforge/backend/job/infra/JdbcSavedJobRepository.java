package com.jobforge.backend.job.infra;

import com.jobforge.backend.job.app.SavedJobRepository;
import com.jobforge.backend.shared.api.PagedResult;
import com.jobforge.backend.shared.persistence.Db;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcSavedJobRepository implements SavedJobRepository {

    private static final String VISIBLE = """
            FROM core.saved_jobs sj JOIN core.jobs j ON j.id = sj.job_id
             WHERE sj.seeker_user_id = :uid AND j.status = 'PUBLISHED' AND j.deleted_at IS NULL
               AND (j.expires_at IS NULL OR j.expires_at > :now)
            """;

    private final JdbcClient jdbc;

    public JdbcSavedJobRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void add(UUID seekerUserId, UUID jobId, Instant now) {
        jdbc.sql("INSERT INTO core.saved_jobs (seeker_user_id, job_id, created_at) VALUES (:uid, :job, :now) ON CONFLICT DO NOTHING")
                .param("uid", seekerUserId).param("job", jobId).param("now", Db.ts(now)).update();
    }

    @Override
    public void remove(UUID seekerUserId, UUID jobId) {
        jdbc.sql("DELETE FROM core.saved_jobs WHERE seeker_user_id = :uid AND job_id = :job")
                .param("uid", seekerUserId).param("job", jobId).update();
    }

    @Override
    public PagedResult<UUID> listVisibleIds(UUID seekerUserId, Instant now, int page, int size) {
        Long total = jdbc.sql("SELECT count(*) " + VISIBLE).param("uid", seekerUserId).param("now", Db.ts(now))
                .query(Long.class).single();
        List<UUID> ids = jdbc.sql("SELECT sj.job_id " + VISIBLE
                        + " ORDER BY sj.created_at DESC, sj.job_id DESC LIMIT :limit OFFSET :offset")
                .param("uid", seekerUserId).param("now", Db.ts(now)).param("limit", size)
                .param("offset", (long) page * size).query((rs, n) -> Db.uuid(rs, "job_id")).list();
        return new PagedResult<>(ids, total);
    }

    @Override
    public Set<UUID> savedAmong(UUID seekerUserId, Collection<UUID> jobIds) {
        if (jobIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(jdbc.sql("SELECT job_id FROM core.saved_jobs WHERE seeker_user_id = :uid AND job_id IN (:ids)")
                .param("uid", seekerUserId).param("ids", jobIds).query((rs, n) -> Db.uuid(rs, "job_id")).list());
    }
}
