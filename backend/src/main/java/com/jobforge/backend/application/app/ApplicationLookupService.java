package com.jobforge.backend.application.app;

import com.jobforge.backend.job.facade.JobApplicationLookup;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements the job module's provider interface and depends only on this module's repository. The module's
 * {@code ApplicationFacade} is implemented by {@link ApplicationFacadeService} (it needs {@code ApplicationService},
 * which would create a bean cycle through the job module if it lived here).
 */
@Service
public class ApplicationLookupService implements JobApplicationLookup {

    private final ApplicationRepository applications;

    public ApplicationLookupService(ApplicationRepository applications) {
        this.applications = applications;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> appliedJobIds(UUID seekerUserId, Collection<UUID> jobIds) {
        return applications.appliedJobIds(seekerUserId, jobIds);
    }

    @Override
    @Transactional(readOnly = true)
    public long applicationCount(UUID jobId) {
        return applications.countByJob(jobId);
    }
}
