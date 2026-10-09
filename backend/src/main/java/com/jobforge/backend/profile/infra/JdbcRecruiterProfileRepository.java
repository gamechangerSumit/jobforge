package com.jobforge.backend.profile.infra;

import com.jobforge.backend.profile.app.RecruiterProfileRepository;
import com.jobforge.backend.profile.domain.ApprovalStatus;
import com.jobforge.backend.profile.domain.RecruiterProfile;
import com.jobforge.backend.shared.persistence.Db;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcRecruiterProfileRepository implements RecruiterProfileRepository {

    private final JdbcClient jdbc;

    public JdbcRecruiterProfileRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<RecruiterProfile> findByUserId(UUID userId) {
        return jdbc.sql("SELECT id, user_id, job_title, phone, approval_status, updated_at FROM core.recruiter_profiles WHERE user_id = :uid")
                .param("uid", userId)
                .query((rs, n) -> new RecruiterProfile(Db.uuid(rs, "id"), Db.uuid(rs, "user_id"), rs.getString("job_title"),
                        rs.getString("phone"), ApprovalStatus.valueOf(rs.getString("approval_status")),
                        Db.instant(rs, "updated_at")))
                .optional();
    }

    @Override
    public void insertEmpty(UUID id, UUID userId, Instant now) {
        jdbc.sql("INSERT INTO core.recruiter_profiles (id, user_id, created_at, updated_at) VALUES (:id, :uid, :now, :now)")
                .param("id", id).param("uid", userId).param("now", Db.ts(now)).update();
    }

    @Override
    public void update(UUID userId, String jobTitle, String phone, Instant now) {
        jdbc.sql("UPDATE core.recruiter_profiles SET job_title = :title, phone = :phone, updated_at = :now WHERE user_id = :uid")
                .param("title", jobTitle).param("phone", phone).param("now", Db.ts(now)).param("uid", userId).update();
    }
}
