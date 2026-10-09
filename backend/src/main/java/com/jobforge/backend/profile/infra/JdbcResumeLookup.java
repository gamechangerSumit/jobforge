package com.jobforge.backend.profile.infra;

import com.jobforge.backend.profile.app.ResumeLookup;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcResumeLookup implements ResumeLookup {

    private final JdbcClient jdbc;

    public JdbcResumeLookup(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean ownsActiveResume(UUID seekerUserId, UUID resumeId) {
        return jdbc.sql("""
                SELECT EXISTS (
                  SELECT 1 FROM core.resumes r JOIN core.seeker_profiles sp ON sp.id = r.seeker_profile_id
                   WHERE r.id = :rid AND sp.user_id = :uid AND r.deleted_at IS NULL)
                """).param("rid", resumeId).param("uid", seekerUserId).query(Boolean.class).single();
    }
}
