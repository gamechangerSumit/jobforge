package com.jobforge.backend.profile.app;

import com.jobforge.backend.profile.domain.SeekerProfile;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface SeekerProfileRepository {

    Optional<SeekerProfile> findByUserId(UUID userId);

    void insertEmpty(UUID id, UUID userId, Instant now);

    /** Optimistic replace of the editable set; @return false when {@code expectedVersion} is stale. */
    /** Clears personal fields and sets visibility PRIVATE (account deletion). */
    void scrub(UUID userId, Instant now);

    /**
     * Recomputes completeness_score (0-100) from stored data without bumping the optimistic-lock version:
     * headline 15, summary 15, location 10, current title 10, >=3 skills 20, education 10, experience 10, primary resume 10.
     */
    void recalculateCompleteness(UUID profileId);

    boolean replace(UUID id, SeekerProfileUpdate update, long expectedVersion, Instant now);
}
