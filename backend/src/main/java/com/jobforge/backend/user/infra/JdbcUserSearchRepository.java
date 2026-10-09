package com.jobforge.backend.user.infra;

import com.jobforge.backend.shared.persistence.Db;
import com.jobforge.backend.user.app.UserSearchRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcUserSearchRepository implements UserSearchRepository {

    private final JdbcClient jdbc;

    public JdbcUserSearchRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<UUID> findSeekerIds(String likePattern, int limit) {
        return jdbc.sql("""
                SELECT id FROM core.users
                 WHERE role = 'JOB_SEEKER' AND status <> 'DELETED' AND deleted_at IS NULL
                   AND ((first_name || ' ' || last_name) ILIKE :p ESCAPE '\\' OR handle::text ILIKE :p ESCAPE '\\')
                 ORDER BY id LIMIT :limit
                """).param("p", likePattern).param("limit", limit)
                .query((rs, n) -> Db.uuid(rs, "id")).list();
    }
}
