package com.jobforge.backend.search.facade;

import java.util.List;

/** Cross-module search capability for building privacy-minimized, authenticated AI context. */
public interface SearchFacade {
    record RecommendationJob(String id, String title, String companyName, String workMode,
            String employmentType, String experienceLevel, List<String> skills,
            String city, String state, String country) {}

    /** Returns public, currently searchable jobs matching a seeker's skills and location. */
    List<RecommendationJob> recommendForSeeker(List<String> skills, String city, String country, int limit);
}
