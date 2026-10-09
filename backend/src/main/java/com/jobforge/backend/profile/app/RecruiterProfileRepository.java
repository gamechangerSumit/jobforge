package com.jobforge.backend.profile.app;

import com.jobforge.backend.profile.domain.RecruiterProfile;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RecruiterProfileRepository {

    Optional<RecruiterProfile> findByUserId(UUID userId);

    void insertEmpty(UUID id, UUID userId, Instant now);

    void update(UUID userId, String jobTitle, String phone, Instant now);
}
