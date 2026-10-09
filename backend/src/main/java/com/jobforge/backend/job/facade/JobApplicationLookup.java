package com.jobforge.backend.job.facade;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * Provider interface declared here and implemented by the application module, so that {@code job} never depends on
 * {@code application} (no module cycles, ARCHITECTURE §4).
 */
public interface JobApplicationLookup {

    Set<UUID> appliedJobIds(UUID seekerUserId, Collection<UUID> jobIds);

    /** All applications of the job, including withdrawn ones (they also block deletion). */
    long applicationCount(UUID jobId);
}
