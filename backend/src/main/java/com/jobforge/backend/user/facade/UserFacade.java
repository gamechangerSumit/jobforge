package com.jobforge.backend.user.facade;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Public API of the user module (ARCHITECTURE §4 rule 2). */
public interface UserFacade {

    Optional<UserAccountView> findById(UUID id);

    Optional<UserAccountView> findByEmail(String email);

    boolean handleTaken(String handle);

    /** @throws com.jobforge.backend.shared.error.ConflictException EMAIL_ALREADY_REGISTERED / HANDLE_TAKEN */
    UserAccountView create(NewUserCommand command);

    void recordLogin(UUID userId, Instant at);

    /** Sets {@code email_verified_at} and promotes PENDING_VERIFICATION to ACTIVE. */
    void markEmailVerified(UUID userId, Instant at);

    void changePassword(UUID userId, String newPasswordHash, boolean bumpTokenVersion);

    /** Account deletion: scrubs PII (email, handle, names, avatar), marks DELETED and invalidates access tokens. */
    void anonymize(UUID userId, String unusablePasswordHash);
}
