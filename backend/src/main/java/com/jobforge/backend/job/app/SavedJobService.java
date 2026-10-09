package com.jobforge.backend.job.app;

import com.jobforge.backend.job.domain.Job;
import com.jobforge.backend.job.facade.JobFacade.Viewer;
import com.jobforge.backend.job.facade.JobViews.JobSummaryView;
import com.jobforge.backend.shared.api.PagedResult;
import com.jobforge.backend.shared.error.ResourceNotFoundException;
import com.jobforge.backend.shared.security.AuthenticatedUser;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** JS-5: saved jobs. Save/unsave are idempotent; only currently public jobs can be saved or listed. */
@Service
public class SavedJobService {

    private final SavedJobRepository saved;
    private final JobRepository jobs;
    private final JobViewAssembler views;
    private final Clock clock;

    public SavedJobService(SavedJobRepository saved, JobRepository jobs, JobViewAssembler views, Clock clock) {
        this.saved = saved;
        this.jobs = jobs;
        this.views = views;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PagedResult<JobSummaryView> list(AuthenticatedUser caller, int page, int size) {
        PagedResult<UUID> ids = saved.listVisibleIds(caller.id(), clock.instant(), page, size);
        List<Job> found = jobs.findByIds(ids.items());
        List<Job> ordered = ids.items().stream()
                .map(id -> found.stream().filter(j -> j.id().equals(id)).findFirst().orElse(null))
                .filter(java.util.Objects::nonNull).toList();
        return new PagedResult<>(views.summaries(ordered, new Viewer(caller.id(), caller.role())), ids.total());
    }

    @Transactional
    public void save(AuthenticatedUser caller, UUID jobId) {
        Job job = jobs.findById(jobId).orElseThrow(() -> new ResourceNotFoundException("Job not found."));
        if (!job.isOpenAt(clock.instant())) {
            throw new ResourceNotFoundException("Job not found.");
        }
        saved.add(caller.id(), jobId, clock.instant());
    }

    @Transactional
    public void unsave(AuthenticatedUser caller, UUID jobId) {
        saved.remove(caller.id(), jobId);
    }
}
