package com.jobforge.backend.search.app;

import com.jobforge.backend.search.domain.JobSearchFilter;
import com.jobforge.backend.search.domain.JobSearchResult;

import java.util.List;

public interface SearchProvider {

    SearchPage search(JobSearchFilter filter);

    com.jobforge.backend.search.domain.JobFacets facets(JobSearchFilter filter);

    /** Published jobs ranked by shared skills then title similarity; empty when the base job is not public. */
    java.util.Optional<List<JobSearchResult>> similar(java.util.UUID jobId, int limit);

    /** Per-job flags for one seeker: bookmarked and/or already applied (withdrawn applications do not count). */
    java.util.Map<java.util.UUID, Flags> flags(java.util.UUID seekerUserId, java.util.Collection<java.util.UUID> jobIds);

    record Flags(boolean saved, boolean applied) {
    }

    record SearchPage(
            List<JobSearchResult> items,
            long total
    ) {
    }
}