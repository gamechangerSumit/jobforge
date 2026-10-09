package com.jobforge.backend.auth.app;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenStore {

    record StoredRefreshToken(UUID id, UUID userId, UUID familyId, Instant expiresAt, Instant revokedAt, UUID replacedById) {}

    void insert(UUID id, UUID userId, UUID familyId, String tokenHash, Instant expiresAt, Instant now, String ip, String userAgent);

    /** Row-locks the token so concurrent refreshes with the same token serialize. */
    Optional<StoredRefreshToken> findByHashForUpdate(String tokenHash);

    void markRotated(UUID id, UUID replacedById, Instant now);

    void revokeFamily(UUID familyId, Instant now);

    void revokeAllForUser(UUID userId, Instant now);

    void revokeAllForUserExceptFamily(UUID userId, UUID familyId, Instant now);
}
