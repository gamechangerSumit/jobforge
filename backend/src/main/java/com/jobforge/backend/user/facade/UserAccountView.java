package com.jobforge.backend.user.facade;

import com.jobforge.backend.shared.domain.UserRole;
import com.jobforge.backend.shared.domain.UserStatus;
import java.time.Instant;
import java.util.UUID;

/** Read model of a user account for other modules. Contains the password hash: NEVER serialize or log it. */
public record UserAccountView(
        UUID id,
        String email,
        String passwordHash,
        UserRole role,
        UserStatus status,
        String firstName,
        String lastName,
        String handle,
        Instant emailVerifiedAt,
        int tokenVersion,
        long version,
        Instant createdAt) {

    public boolean emailVerified() {
        return emailVerifiedAt != null;
    }

    @Override
    public String toString() {
        return "UserAccountView[id=" + id + ", role=" + role + ", status=" + status + "]";
    }
}
