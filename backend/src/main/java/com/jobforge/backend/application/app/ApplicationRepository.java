package com.jobforge.backend.application.app;

import com.jobforge.backend.application.domain.Application;
import com.jobforge.backend.application.domain.ApplicationStatus;
import com.jobforge.backend.application.domain.StatusHistoryEntry;
import com.jobforge.backend.shared.api.PagedResult;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface ApplicationRepository {

    /** Unique on (job, seeker): @return false when the seeker already has an application to the job (D-17). */
    boolean insertIfAbsent(Application application, Instant now);

    Optional<Application> findById(UUID id);

    Optional<Application> findByJobAndSeeker(UUID jobId, UUID seekerUserId);

    /** Optimistic status update; @return false when {@code expectedVersion} is stale. */
    boolean updateStatus(UUID id, ApplicationStatus to, long expectedVersion, Instant now, boolean withdrawn);

    /** Sets or replaces the rating; bumps the version. */
    void updateRating(UUID id, int rating, Instant now);

    void addHistory(StatusHistoryEntry entry);

    List<StatusHistoryEntry> history(UUID applicationId);

    PagedResult<Application> findBySeeker(UUID seekerUserId, ApplicationStatus status, String orderBy, int page, int size);

    /** Applications to any of {@code jobIds}, optionally narrowed by status, a single job and applicant ids. */
    PagedResult<Application> findByJobs(Collection<UUID> jobIds, ApplicationStatus status, Collection<UUID> seekerIds,
            String orderBy, int page, int size);

    long countByJob(UUID jobId);

    Set<UUID> appliedJobIds(UUID seekerUserId, Collection<UUID> jobIds);

    boolean existsActive(UUID seekerUserId, Collection<UUID> jobIds);

    /** Applications by id (missing ids are simply absent). */
    List<Application> findAllByIds(Collection<UUID> ids);

    /** Ids of all applications to any of the given jobs. */
    List<UUID> idsByJobs(Collection<UUID> jobIds);

    /** Ids of all applications of the seeker. */
    List<UUID> idsBySeeker(UUID seekerUserId);
}
