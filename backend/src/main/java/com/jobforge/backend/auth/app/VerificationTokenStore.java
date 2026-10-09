package com.jobforge.backend.auth.app;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface VerificationTokenStore {

    enum Type { EMAIL_VERIFICATION, PASSWORD_RESET }

    record StoredVerificationToken(UUID id, UUID userId, Instant expiresAt, Instant usedAt) {}

    void insert(UUID id, UUID userId, Type type, String tokenHash, Instant expiresAt, Instant now);

    Optional<StoredVerificationToken> findByHashForUpdate(String tokenHash, Type type);

    void markUsed(UUID id, Instant now);

    /** Hard-deletes unused tokens of a type so only the newest link works. */
    void deleteUnused(UUID userId, Type type);
}
