package com.jobforge.backend.user.facade;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-only user lookups for the community module (Session 4, additive): public summaries (API_CONTRACT 11
 * UserSummary), handle resolution for @mentions and an "is active" check. Never exposes e-mail or password data.
 */
public interface UserSummaryFacade {

    /** Public summary; deleted accounts are returned anonymized ({@code "Deleted"/"User"}). */
    record UserSummaryView(UUID id, String handle, String firstName, String lastName, String avatarUrl, String role,
            boolean active) {}

    /** Missing ids are absent from the map. */
    Map<UUID, UserSummaryView> summaries(Collection<UUID> ids);

    Optional<UserSummaryView> summary(UUID id);

    /** Active users only, keyed by lower-case handle; unknown/inactive handles are absent (API_CONTRACT 12.9). */
    Map<String, UUID> activeUserIdsByHandle(Collection<String> handles);
}
