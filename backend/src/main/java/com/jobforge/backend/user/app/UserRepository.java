package com.jobforge.backend.user.app;

import com.jobforge.backend.user.facade.NewUserCommand;
import com.jobforge.backend.user.facade.UserAccountView;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Port implemented in {@code infra}. Excludes soft-deleted rows. */
public interface UserRepository {

    Optional<UserAccountView> findById(UUID id);

    Optional<UserAccountView> findByEmail(String email);

    boolean handleExists(String handle);

    /** @throws com.jobforge.backend.shared.error.ConflictException on duplicate email/handle */
    void insert(UUID id, NewUserCommand command, Instant now);

    /** Optimistic update; @return false when {@code expectedVersion} is stale. */
    boolean updateNames(UUID id, String firstName, String lastName, String handle, long expectedVersion, Instant now);

    void recordLogin(UUID id, Instant at);

    void markEmailVerified(UUID id, Instant at);

    void updatePassword(UUID id, String passwordHash, boolean bumpTokenVersion, Instant at);

    Optional<String> findAvatarKey(UUID id);

    /** Sets (or clears with null) the avatar key. */
    void setAvatarKey(UUID id, String key, Instant at);

    /** Account deletion: scrubs PII, sets status DELETED, soft-deletes and bumps token_version. */
    void anonymize(UUID id, String email, String handle, String passwordHash, Instant at);
}
