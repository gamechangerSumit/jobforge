package com.jobforge.backend.search.app;

import com.jobforge.backend.search.domain.JobFacets;
import com.jobforge.backend.search.domain.JobSearchFilter;
import com.jobforge.backend.search.domain.JobSearchResult;
import com.jobforge.backend.shared.api.PageMeta;
import com.jobforge.backend.shared.api.PagedResponse;
import com.jobforge.backend.shared.error.ResourceNotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobSearchService {

    private static final int SIMILAR_LIMIT = 10;

    private static final java.time.Duration FACETS_TTL = java.time.Duration.ofSeconds(60);

    private final SearchProvider searchProvider;
    private final com.jobforge.backend.shared.cache.CacheService cache;

    public JobSearchService(SearchProvider searchProvider, com.jobforge.backend.shared.cache.CacheService cache) {
        this.searchProvider = searchProvider;
        this.cache = cache;
    }

    @Transactional(readOnly = true)
    public PagedResponse<JobSearchResult> search(JobSearchFilter filter) {
        return search(filter, null);
    }

    /** {@code seekerUserId} is the signed-in JOB_SEEKER (or null): when set, saved/applied flags are filled in. */
    @Transactional(readOnly = true)
    public PagedResponse<JobSearchResult> search(JobSearchFilter filter, UUID seekerUserId) {
        SearchProvider.SearchPage result = searchProvider.search(filter);
        return new PagedResponse<>(withFlags(result.items(), seekerUserId),
                PageMeta.of(filter.page(), filter.size(), result.total()));
    }

    @Transactional(readOnly = true)
    public JobFacets facets(JobSearchFilter filter) {
        // Facets are public and depend only on the filter (a record: stable toString), so they are cached by its hash.
        return cache.getOrLoad("facets:" + sha256(filter.toString()), FACETS_TTL,
                new com.fasterxml.jackson.core.type.TypeReference<JobFacets>() { }, () -> searchProvider.facets(filter));
    }

    @Transactional(readOnly = true)
    public List<JobSearchResult> similar(UUID jobId) {
        return similar(jobId, null);
    }

    @Transactional(readOnly = true)
    public List<JobSearchResult> similar(UUID jobId, UUID seekerUserId) {
        List<JobSearchResult> items = searchProvider.similar(jobId, SIMILAR_LIMIT)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found."));
        return withFlags(items, seekerUserId);
    }

    private List<JobSearchResult> withFlags(List<JobSearchResult> items, UUID seekerUserId) {
        if (seekerUserId == null || items.isEmpty()) {
            return items;
        }
        java.util.Map<UUID, SearchProvider.Flags> flags =
                searchProvider.flags(seekerUserId, items.stream().map(JobSearchResult::id).toList());
        return items.stream().map(item -> {
            SearchProvider.Flags f = flags.get(item.id());
            return item.withFlags(f != null && f.saved(), f != null && f.applied());
        }).toList();
    }

    private static String sha256(String value) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
