package com.jobforge.backend.profile.infra;

import com.jobforge.backend.profile.app.RecruiterApprovalRepository;
import com.jobforge.backend.shared.persistence.Db;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcRecruiterApprovalRepository implements RecruiterApprovalRepository {

    private final JdbcClient jdbc;

    public JdbcRecruiterApprovalRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Row> list(String status, int limit, int offset) {
        boolean filter = status != null;
        String sql = """
                SELECT u.id, u.email::text AS email, u.first_name, u.last_name, rp.job_title, rp.approval_status,
                       rp.rejection_reason, rp.created_at,
                       (SELECT c.name FROM core.company_members m JOIN core.companies c ON c.id = m.company_id
                         WHERE m.user_id = u.id AND c.deleted_at IS NULL) AS company_name
                  FROM core.recruiter_profiles rp JOIN core.users u ON u.id = rp.user_id
                 WHERE u.deleted_at IS NULL
                """ + (filter ? " AND rp.approval_status = :status" : "")
                + " ORDER BY rp.created_at DESC LIMIT :limit OFFSET :offset";
        JdbcClient.StatementSpec spec = jdbc.sql(sql).param("limit", limit).param("offset", offset);
        if (filter) {
            spec = spec.param("status", status);
        }
        return spec.query((rs, n) -> new Row(Db.uuid(rs, "id"), rs.getString("email"), rs.getString("first_name"),
                rs.getString("last_name"), rs.getString("job_title"), rs.getString("approval_status"),
                rs.getString("rejection_reason"), rs.getString("company_name"), Db.instant(rs, "created_at"))).list();
    }

    @Override
    public Optional<Detail> find(UUID userId) {
        return jdbc.sql("""
                SELECT u.id, u.email::text AS email, u.first_name, u.last_name, rp.job_title, rp.phone, rp.approval_status,
                       rp.rejection_reason, rp.created_at,
                       (SELECT c.name FROM core.company_members m JOIN core.companies c ON c.id = m.company_id
                         WHERE m.user_id = u.id AND c.deleted_at IS NULL) AS company_name,
                       (SELECT c.id FROM core.company_members m JOIN core.companies c ON c.id = m.company_id
                         WHERE m.user_id = u.id AND c.deleted_at IS NULL) AS company_id
                  FROM core.recruiter_profiles rp JOIN core.users u ON u.id = rp.user_id
                 WHERE u.id = :uid AND u.deleted_at IS NULL
                """).param("uid", userId).query((rs, n) -> new Detail(
                new Row(Db.uuid(rs, "id"), rs.getString("email"), rs.getString("first_name"), rs.getString("last_name"),
                        rs.getString("job_title"), rs.getString("approval_status"), rs.getString("rejection_reason"),
                        rs.getString("company_name"), Db.instant(rs, "created_at")),
                rs.getString("phone"), Db.uuid(rs, "company_id"))).optional();
    }

    @Override
    public long count(String status) {
        boolean filter = status != null;
        JdbcClient.StatementSpec spec = jdbc.sql("SELECT count(*) FROM core.recruiter_profiles rp "
                + "JOIN core.users u ON u.id = rp.user_id WHERE u.deleted_at IS NULL"
                + (filter ? " AND rp.approval_status = :status" : ""));
        if (filter) {
            spec = spec.param("status", status);
        }
        return spec.query(Long.class).single();
    }

    @Override
    public Optional<String> setStatus(UUID userId, String status, UUID adminId, String reason, Instant now) {
        Optional<String> previous = jdbc.sql("SELECT approval_status FROM core.recruiter_profiles "
                + "WHERE user_id = :uid FOR UPDATE").param("uid", userId).query(String.class).optional();
        if (previous.isEmpty()) {
            return previous;
        }
        boolean approved = "APPROVED".equals(status);
        jdbc.sql("""
                UPDATE core.recruiter_profiles SET approval_status = :status, approved_by = :by, approved_at = :at,
                       rejection_reason = :reason, updated_at = :now WHERE user_id = :uid
                """).param("status", status).param("by", approved ? adminId : null)
                .param("at", approved ? Db.ts(now) : null).param("reason", approved ? null : reason)
                .param("now", Db.ts(now)).param("uid", userId).update();
        return previous;
    }
}
