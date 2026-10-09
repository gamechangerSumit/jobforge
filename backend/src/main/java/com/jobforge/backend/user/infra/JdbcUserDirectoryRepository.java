package com.jobforge.backend.user.infra;

import com.jobforge.backend.shared.persistence.Db;
import com.jobforge.backend.user.app.UserDirectory.AdminUserRow;
import com.jobforge.backend.user.app.UserDirectory.PublicCard;
import com.jobforge.backend.user.app.UserDirectory;
import com.jobforge.backend.user.app.UserDirectory.UserSummary;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

@org.springframework.stereotype.Repository
public class JdbcUserDirectoryRepository implements UserDirectory.Repository {

    private static final String ADMIN_SELECT = """
            SELECT u.id, u.email::text AS email, u.first_name, u.last_name, u.handle::text AS handle, u.role, u.status,
                   (u.email_verified_at IS NOT NULL) AS email_verified, u.created_at, u.last_login_at
              FROM core.users u WHERE u.deleted_at IS NULL
            """;

    private final JdbcClient jdbc;

    public JdbcUserDirectoryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private static AdminUserRow mapAdmin(ResultSet rs, int n) throws SQLException {
        return new AdminUserRow(Db.uuid(rs, "id"), rs.getString("email"), rs.getString("first_name"),
                rs.getString("last_name"), rs.getString("handle"), rs.getString("role"), rs.getString("status"),
                rs.getBoolean("email_verified"), Db.instant(rs, "created_at"), Db.instant(rs, "last_login_at"));
    }

    @Override
    public List<UserSummary> searchActive(String like, int limit) {
        return jdbc.sql("""
                SELECT id, first_name, last_name, handle::text AS handle FROM core.users
                 WHERE deleted_at IS NULL AND status = 'ACTIVE'
                   AND (handle::text ILIKE :q ESCAPE '\\' OR (first_name || ' ' || last_name) ILIKE :q ESCAPE '\\')
                 ORDER BY handle LIMIT :limit
                """).param("q", like).param("limit", limit)
                .query((rs, n) -> new UserSummary(Db.uuid(rs, "id"), rs.getString("first_name"),
                        rs.getString("last_name"), rs.getString("handle")))
                .list();
    }

    @Override
    public Optional<PublicCard> publicCard(UUID id) {
        return jdbc.sql("""
                SELECT u.id, u.first_name, u.last_name, u.handle::text AS handle, u.role, sp.headline
                  FROM core.users u LEFT JOIN core.seeker_profiles sp ON sp.user_id = u.id AND sp.visibility = 'PUBLIC'
                 WHERE u.id = :id AND u.deleted_at IS NULL AND u.status = 'ACTIVE'
                """).param("id", id)
                .query((rs, n) -> new PublicCard(Db.uuid(rs, "id"), rs.getString("first_name"),
                        rs.getString("last_name"), rs.getString("handle"), rs.getString("role"),
                        rs.getString("headline")))
                .optional();
    }

    private static String filters(String like, String role, String status) {
        return (like != null ? " AND (u.email::text ILIKE :q ESCAPE '\\' OR u.handle::text ILIKE :q ESCAPE '\\'"
                + " OR (u.first_name || ' ' || u.last_name) ILIKE :q ESCAPE '\\')" : "")
                + (role != null ? " AND u.role = :role" : "") + (status != null ? " AND u.status = :status" : "");
    }

    private static JdbcClient.StatementSpec bind(JdbcClient.StatementSpec spec, String like, String role, String status) {
        if (like != null) {
            spec = spec.param("q", like);
        }
        if (role != null) {
            spec = spec.param("role", role);
        }
        if (status != null) {
            spec = spec.param("status", status);
        }
        return spec;
    }

    @Override
    public List<AdminUserRow> adminSearch(String like, String role, String status, int limit, int offset) {
        JdbcClient.StatementSpec spec = jdbc.sql(ADMIN_SELECT + filters(like, role, status)
                + " ORDER BY u.created_at DESC LIMIT :limit OFFSET :offset").param("limit", limit).param("offset", offset);
        return bind(spec, like, role, status).query(JdbcUserDirectoryRepository::mapAdmin).list();
    }

    @Override
    public long adminCount(String like, String role, String status) {
        JdbcClient.StatementSpec spec = jdbc.sql("SELECT count(*) FROM core.users u WHERE u.deleted_at IS NULL"
                + filters(like, role, status));
        return bind(spec, like, role, status).query(Long.class).single();
    }

    @Override
    public Optional<AdminUserRow> adminFind(UUID id) {
        return jdbc.sql(ADMIN_SELECT + " AND u.id = :id").param("id", id)
                .query(JdbcUserDirectoryRepository::mapAdmin).optional();
    }

    @Override
    public Optional<String> setStatus(UUID id, String status, Instant now) {
        Optional<String> previous = jdbc.sql("SELECT status FROM core.users WHERE id = :id AND deleted_at IS NULL FOR UPDATE")
                .param("id", id).query(String.class).optional();
        if (previous.isPresent()) {
            jdbc.sql("UPDATE core.users SET status = :s, token_version = token_version + 1, version = version + 1, "
                    + "updated_at = :now WHERE id = :id").param("s", status).param("now", Db.ts(now)).param("id", id).update();
        }
        return previous;
    }

    @Override
    public boolean bumpTokenVersion(UUID id, Instant now) {
        return jdbc.sql("UPDATE core.users SET token_version = token_version + 1, updated_at = :now "
                + "WHERE id = :id AND deleted_at IS NULL").param("now", Db.ts(now)).param("id", id).update() > 0;
    }
}
