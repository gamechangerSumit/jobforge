package com.jobforge.backend.job.facade;

import com.jobforge.backend.job.facade.JobViews.JobAdminView;
import com.jobforge.backend.job.facade.JobViews.JobApplyView;
import com.jobforge.backend.job.facade.JobViews.JobLiteView;
import com.jobforge.backend.job.facade.JobViews.JobSummaryView;
import com.jobforge.backend.shared.api.PagedResult;
import com.jobforge.backend.shared.domain.UserRole;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Public API of the job module. The search module (Dev 3) ranks job ids and then calls {@link #getSummaries} so
 * that JobSummary shape, salary visibility and saved/applied flags stay owned by this module.
 */
public interface JobFacade {

    /** The caller on whose behalf summaries are built; {@link #ANONYMOUS} for public traffic. */
    record Viewer(UUID userId, UserRole role) {
        public static final Viewer ANONYMOUS = new Viewer(null, null);
    }

    /** Publicly visible jobs only (PUBLISHED, not expired, not deleted), in the order of {@code ids}. */
    List<JobSummaryView> getSummaries(Collection<UUID> ids, Viewer viewer);

    Optional<JobApplyView> getForApply(UUID jobId);

    /** Non-deleted jobs regardless of status; used to render applications of jobs that are no longer public. */
    Map<UUID, JobLiteView> getLites(Collection<UUID> jobIds);

    Optional<UUID> companyIdOf(UUID jobId);

    List<UUID> jobIdsOfCompany(UUID companyId);

    PagedResult<JobAdminView> adminSearch(String q, String status, UUID companyId, int page, int size);

    JobAdminView removeJob(UUID adminUserId, UUID jobId, String reason);

    JobAdminView restoreJob(UUID adminUserId, UUID jobId);
}
