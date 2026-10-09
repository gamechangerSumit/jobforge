package com.jobforge.backend.user.infra;

import com.jobforge.backend.shared.domain.UserRole;
import com.jobforge.backend.shared.domain.UserStatus;
import com.jobforge.backend.shared.error.ConflictException;
import com.jobforge.backend.shared.error.ErrorCode;
import com.jobforge.backend.shared.persistence.Db;
import com.jobforge.backend.user.app.UserRepository;
import com.jobforge.backend.user.facade.NewUserCommand;
import com.jobforge.backend.user.facade.UserAccountView;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcUserRepository implements UserRepository {

    private static final String COLUMNS = """
            id, email, password_hash, role, status, first_name, last_name, handle,
            email_verified_at, token_version, version, created_at
            """;

    private final JdbcClient jdbc;

    public JdbcUserRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<UserAccountView> findById(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM core.users WHERE id = :id AND deleted_at IS NULL")
                .param("id", id).query(JdbcUserRepository::map).optional();
    }

    @Override
    public Optional<UserAccountView> findByEmail(String email) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM core.users WHERE email = :email AND deleted_at IS NULL")
                .param("email", email).query(JdbcUserRepository::map).optional();
    }

    @Override
    public boolean handleExists(String handle) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM core.users WHERE handle = :handle)")
                .param("handle", handle).query(Boolean.class).single();
    }

    @Override
    public void insert(UUID id, NewUserCommand c, Instant now) {
        try {
            jdbc.sql("""
                    INSERT INTO core.users
                      (id, email, password_hash, role, status, first_name, last_name, handle,
                       token_version, version, created_at, updated_at)
                    VALUES
                      (:id, :email, :hash, :role, 'PENDING_VERIFICATION', :first, :last, :handle,
                       0, 0, :now, :now)
                    """)
                    .param("id", id).param("email", c.email()).param("hash", c.passwordHash())
                    .param("role", c.role().name()).param("first", c.firstName()).param("last", c.lastName())
                    .param("handle", c.handle()).param("now", Db.ts(now))
                    .update();
        } catch (DuplicateKeyException e) {
            throw translate(e);
        }
    }

    @Override
    public boolean updateNames(UUID id, String firstName, String lastName, String handle, long expectedVersion, Instant now) {
        try {
            return jdbc.sql("""
                    UPDATE core.users
                       SET first_name = :first, last_name = :last, handle = :handle,
                           version = version + 1, updated_at = :now
                     WHERE id = :id AND version = :ver AND deleted_at IS NULL
                    """)
                    .param("first", firstName).param("last", lastName).param("handle", handle)
                    .param("now", Db.ts(now)).param("id", id).param("ver", expectedVersion)
                    .update() == 1;
        } catch (DuplicateKeyException e) {
            throw translate(e);
        }
    }

    @Override
    public void recordLogin(UUID id, Instant at) {
        jdbc.sql("UPDATE core.users SET last_login_at = :at, updated_at = :at WHERE id = :id")
                .param("at", Db.ts(at)).param("id", id).update();
    }

    @Override
    public void markEmailVerified(UUID id, Instant at) {
        jdbc.sql("""
                UPDATE core.users
                   SET email_verified_at = COALESCE(email_verified_at, :at),
                       status = CASE WHEN status = 'PENDING_VERIFICATION' THEN 'ACTIVE' ELSE status END,
                       version = version + 1, updated_at = :at
                 WHERE id = :id
                """).param("at", Db.ts(at)).param("id", id).update();
    }

    @Override
    public void updatePassword(UUID id, String passwordHash, boolean bumpTokenVersion, Instant at) {
        jdbc.sql("""
                UPDATE core.users
                   SET password_hash = :hash,
                       token_version = token_version + CASE WHEN :bump THEN 1 ELSE 0 END,
                       version = version + 1, updated_at = :at
                 WHERE id = :id
                """).param("hash", passwordHash).param("bump", bumpTokenVersion)
                .param("at", Db.ts(at)).param("id", id).update();
    }

    @Override
    public Optional<String> findAvatarKey(UUID id) {
        java.util.List<String> keys = jdbc.sql("SELECT avatar_key FROM core.users WHERE id = :id AND deleted_at IS NULL")
                .param("id", id).query(String.class).list();
        return keys.isEmpty() || keys.get(0) == null ? Optional.empty() : Optional.of(keys.get(0));
    }

    @Override
    public void setAvatarKey(UUID id, String key, Instant at) {
        jdbc.sql("""
                UPDATE core.users SET avatar_key = :key, version = version + 1, updated_at = :at
                 WHERE id = :id AND deleted_at IS NULL
                """).param("key", key).param("at", Db.ts(at)).param("id", id).update();
    }

    @Override
    public void anonymize(UUID id, String email, String handle, String passwordHash, Instant at) {
        jdbc.sql("""
                UPDATE core.users
                   SET email = :email, handle = :handle, password_hash = :hash, first_name = 'Deleted', last_name = 'User',
                       avatar_key = NULL, status = 'DELETED', deleted_at = :at, token_version = token_version + 1,
                       version = version + 1, updated_at = :at
                 WHERE id = :id
                """).param("email", email).param("handle", handle).param("hash", passwordHash)
                .param("at", Db.ts(at)).param("id", id).update();
    }

    private static ConflictException translate(DuplicateKeyException e) {
        String message = String.valueOf(e.getMessage());
        if (message.contains("uq_users_email")) {
            return new ConflictException(ErrorCode.EMAIL_ALREADY_REGISTERED, "This email address is already registered.");
        }
        if (message.contains("uq_users_handle")) {
            return new ConflictException(ErrorCode.HANDLE_TAKEN, "This handle is already taken.");
        }
        return new ConflictException("The request conflicts with an existing resource.");
    }

    private static UserAccountView map(ResultSet rs, int row) throws SQLException {
        return new UserAccountView(
                Db.uuid(rs, "id"), rs.getString("email"), rs.getString("password_hash"),
                UserRole.valueOf(rs.getString("role")), UserStatus.valueOf(rs.getString("status")),
                rs.getString("first_name"), rs.getString("last_name"), rs.getString("handle"),
                Db.instant(rs, "email_verified_at"), rs.getInt("token_version"), rs.getLong("version"),
                Db.instant(rs, "created_at"));
    }
}
