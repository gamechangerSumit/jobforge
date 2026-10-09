package com.jobforge.backend.search.domain;

import java.util.List;

/** Facet counts for the current filter set (API_CONTRACT 12.4 {@code GET /jobs/facets}). */
public record JobFacets(
        List<Bucket> workMode,
        List<Bucket> employmentType,
        List<Bucket> experienceLevel,
        List<Bucket> locations,
        List<Bucket> skills) {

    public record Bucket(String value, long count) {}
}
