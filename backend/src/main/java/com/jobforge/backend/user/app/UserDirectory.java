package com.jobforge.backend.user.app;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Directory read models and port (API_CONTRACT 12.1 public lookups, 12.14 admin user management). */
public final class UserDirectory {

    private UserDirectory() {}

    public record UserSummary(UUID id, String firstName, String lastName, String handle) {}

    public record PublicCard(UUID id, String firstName, String lastName, String handle, String role, String headline) {}

    public record AdminUserRow(UUID id, String email, String firstName, String lastName, String handle, String role,
            String status, boolean emailVerified, Instant createdAt, Instant lastLoginAt) {}

    public interface Repository {
        List<UserSummary> searchActive(String likePattern, int limit);

        Optional<PublicCard> publicCard(UUID id);

        List<AdminUserRow> adminSearch(String likePattern, String role, String status, int limit, int offset);

        long adminCount(String likePattern, String role, String status);

        Optional<AdminUserRow> adminFind(UUID id);

        /** Returns previous status, empty when the user does not exist. Bumps token_version to revoke access tokens. */
        Optional<String> setStatus(UUID id, String status, Instant now);

        boolean bumpTokenVersion(UUID id, Instant now);
    }
}
