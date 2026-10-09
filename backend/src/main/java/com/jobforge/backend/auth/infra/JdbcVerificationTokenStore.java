package com.jobforge.backend.auth.infra;

import com.jobforge.backend.auth.app.VerificationTokenStore;
import com.jobforge.backend.shared.persistence.Db;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcVerificationTokenStore implements VerificationTokenStore {

    private final JdbcClient jdbc;

    public JdbcVerificationTokenStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(UUID id, UUID userId, Type type, String tokenHash, Instant expiresAt, Instant now) {
        jdbc.sql("""
                INSERT INTO core.verification_tokens (id, user_id, type, token_hash, expires_at, created_at)
                VALUES (:id, :uid, :type, :hash, :exp, :now)
                """)
                .param("id", id).param("uid", userId).param("type", type.name()).param("hash", tokenHash)
                .param("exp", Db.ts(expiresAt)).param("now", Db.ts(now)).update();
    }

    @Override
    public Optional<StoredVerificationToken> findByHashForUpdate(String tokenHash, Type type) {
        return jdbc.sql("""
                SELECT id, user_id, expires_at, used_at FROM core.verification_tokens
                 WHERE token_hash = :hash AND type = :type FOR UPDATE
                """).param("hash", tokenHash).param("type", type.name())
                .query((rs, n) -> new StoredVerificationToken(Db.uuid(rs, "id"), Db.uuid(rs, "user_id"),
                        Db.instant(rs, "expires_at"), Db.instant(rs, "used_at")))
                .optional();
    }

    @Override
    public void markUsed(UUID id, Instant now) {
        jdbc.sql("UPDATE core.verification_tokens SET used_at = :now WHERE id = :id")
                .param("now", Db.ts(now)).param("id", id).update();
    }

    @Override
    public void deleteUnused(UUID userId, Type type) {
        jdbc.sql("DELETE FROM core.verification_tokens WHERE user_id = :uid AND type = :type AND used_at IS NULL")
                .param("uid", userId).param("type", type.name()).update();
    }
}
