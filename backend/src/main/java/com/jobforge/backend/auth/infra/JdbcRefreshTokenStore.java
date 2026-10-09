package com.jobforge.backend.auth.infra;

import com.jobforge.backend.auth.app.RefreshTokenStore;
import com.jobforge.backend.shared.persistence.Db;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcRefreshTokenStore implements RefreshTokenStore {

    private final JdbcClient jdbc;

    public JdbcRefreshTokenStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(UUID id, UUID userId, UUID familyId, String tokenHash, Instant expiresAt, Instant now, String ip, String userAgent) {
        jdbc.sql("""
                INSERT INTO core.refresh_tokens (id, user_id, family_id, token_hash, expires_at, created_at, ip, user_agent)
                VALUES (:id, :uid, :fid, :hash, :exp, :now, CAST(:ip AS inet), :ua)
                """)
                .param("id", id).param("uid", userId).param("fid", familyId).param("hash", tokenHash)
                .param("exp", Db.ts(expiresAt)).param("now", Db.ts(now)).param("ip", ip).param("ua", userAgent)
                .update();
    }

    @Override
    public Optional<StoredRefreshToken> findByHashForUpdate(String tokenHash) {
        return jdbc.sql("""
                SELECT id, user_id, family_id, expires_at, revoked_at, replaced_by_id
                  FROM core.refresh_tokens WHERE token_hash = :hash FOR UPDATE
                """).param("hash", tokenHash)
                .query((rs, n) -> new StoredRefreshToken(Db.uuid(rs, "id"), Db.uuid(rs, "user_id"), Db.uuid(rs, "family_id"),
                        Db.instant(rs, "expires_at"), Db.instant(rs, "revoked_at"), Db.uuid(rs, "replaced_by_id")))
                .optional();
    }

    @Override
    public void markRotated(UUID id, UUID replacedById, Instant now) {
        jdbc.sql("UPDATE core.refresh_tokens SET revoked_at = :now, replaced_by_id = :rid WHERE id = :id")
                .param("now", Db.ts(now)).param("rid", replacedById).param("id", id).update();
    }

    @Override
    public void revokeFamily(UUID familyId, Instant now) {
        jdbc.sql("UPDATE core.refresh_tokens SET revoked_at = COALESCE(revoked_at, :now) WHERE family_id = :fid")
                .param("now", Db.ts(now)).param("fid", familyId).update();
    }

    @Override
    public void revokeAllForUser(UUID userId, Instant now) {
        jdbc.sql("UPDATE core.refresh_tokens SET revoked_at = COALESCE(revoked_at, :now) WHERE user_id = :uid")
                .param("now", Db.ts(now)).param("uid", userId).update();
    }

    @Override
    public void revokeAllForUserExceptFamily(UUID userId, UUID familyId, Instant now) {
        jdbc.sql("UPDATE core.refresh_tokens SET revoked_at = COALESCE(revoked_at, :now) WHERE user_id = :uid AND family_id <> :fid")
                .param("now", Db.ts(now)).param("uid", userId).param("fid", familyId).update();
    }
}
