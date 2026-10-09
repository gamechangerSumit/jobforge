package com.jobforge.backend.job.app;

import com.jobforge.backend.job.domain.Job;
import com.jobforge.backend.job.domain.JobStatus;
import com.jobforge.backend.shared.api.PagedResult;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence port. All reads exclude soft-deleted rows; skills are always loaded. */
public interface JobRepository {

    record ExpiredJob(UUID id, UUID companyId) {}

    void insert(Job job, Instant now);

    Optional<Job> findById(UUID id);

    List<Job> findByIds(Collection<UUID> ids);

    /** Optimistic update of content + status columns; @return false when {@code expectedVersion} is stale. */
    boolean update(Job job, long expectedVersion, Instant now, boolean replaceSkills);

    boolean softDelete(UUID id, long expectedVersion, Instant now);

    List<UUID> findIdsByCompany(UUID companyId);

    PagedResult<Job> findByCompany(UUID companyId, JobStatus status, String q, int page, int size);

    PagedResult<Job> adminSearch(JobStatus status, String q, UUID companyId, int page, int size);

    /** PUBLISHED jobs past {@code expires_at} → EXPIRED. */
    List<ExpiredJob> expireDue(Instant now);
}
