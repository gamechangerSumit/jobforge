package com.jobforge.backend.user.api;

import com.jobforge.backend.shared.domain.UserRole;
import com.jobforge.backend.user.facade.UserAccountView;
import java.time.Instant;
import java.util.UUID;

/** Account fields for {@code GET/PATCH /users/me}. Never contains the password hash. */
public record UserAccountResponse(
        UUID id,
        String email,
        UserRole role,
        String firstName,
        String lastName,
        String handle,
        String avatarUrl,
        boolean emailVerified,
        Instant createdAt) {

    static UserAccountResponse from(UserAccountView u, String avatarUrl) {
        return new UserAccountResponse(u.id(), u.email(), u.role(), u.firstName(), u.lastName(), u.handle(),
                avatarUrl, u.emailVerified(), u.createdAt());
    }
}
