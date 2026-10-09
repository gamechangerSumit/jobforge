package com.jobforge.backend.job.app;

import com.jobforge.backend.shared.api.PagedResult;
import java.time.Instant;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;

public interface SavedJobRepository {

    /** Idempotent insert. */
    void add(UUID seekerUserId, UUID jobId, Instant now);

    /** Idempotent delete. */
    void remove(UUID seekerUserId, UUID jobId);

    /** Newest first; only jobs that are currently public. */
    PagedResult<UUID> listVisibleIds(UUID seekerUserId, Instant now, int page, int size);

    Set<UUID> savedAmong(UUID seekerUserId, Collection<UUID> jobIds);
}
