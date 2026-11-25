package com.jobforge.backend.search.facade;

import com.jobforge.backend.search.domain.JobSearchResult;
import java.util.List;
import java.util.UUID;

/** Cross-module search capability for building privacy-minimized, authenticated AI context. */
public interface SearchFacade {
    /** Returns public, currently searchable jobs matching a seeker's skills and location. */
    List<JobSearchResult> recommendForSeeker(List<String> skills, String city, String country, int limit);
}
