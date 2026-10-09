package com.jobforge.backend.search.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jobforge.backend.search.domain.JobSearchFilter;
import com.jobforge.backend.search.domain.JobSearchResult;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JobSearchServiceFlagsTest {

    private final SearchProvider provider = mock(SearchProvider.class);
    private final JobSearchService service = new JobSearchService(provider, new com.jobforge.backend.platform.cache.NoopCacheService());

    private static JobSearchResult job(UUID id) {
        return new JobSearchResult(id, "Backend Engineer", null, null, "REMOTE", "FULL_TIME", "MID", null, List.of(),
                null, null, null, 1.0);
    }

    @Test
    void similarFillsFlagsForTheSeekerAndDefaultsMissingEntriesToFalse() {
        UUID base = UUID.randomUUID();
        UUID saved = UUID.randomUUID();
        UUID applied = UUID.randomUUID();
        UUID plain = UUID.randomUUID();
        UUID seeker = UUID.randomUUID();
        when(provider.similar(base, 10)).thenReturn(Optional.of(List.of(job(saved), job(applied), job(plain))));
        when(provider.flags(seeker, List.of(saved, applied, plain))).thenReturn(Map.of(
                saved, new SearchProvider.Flags(true, false),
                applied, new SearchProvider.Flags(false, true)));

        List<JobSearchResult> result = service.similar(base, seeker);

        assertThat(result).extracting(JobSearchResult::saved).containsExactly(true, false, false);
        assertThat(result).extracting(JobSearchResult::applied).containsExactly(false, true, false);
    }

    @Test
    void anonymousCallersKeepNullFlagsAndNeverQueryFlags() {
        UUID base = UUID.randomUUID();
        when(provider.similar(base, 10)).thenReturn(Optional.of(List.of(job(UUID.randomUUID()))));

        List<JobSearchResult> result = service.similar(base, null);

        assertThat(result.get(0).saved()).isNull();
        assertThat(result.get(0).applied()).isNull();
        verify(provider, never()).flags(any(), any());
    }

    @Test
    void emptyResultsDoNotQueryFlags() {
        UUID base = UUID.randomUUID();
        when(provider.similar(base, 10)).thenReturn(Optional.of(List.of()));
        assertThat(service.similar(base, UUID.randomUUID())).isEmpty();
        verify(provider, never()).flags(any(), any());
    }

    @Test
    void searchFillsFlagsAndKeepsPaging() {
        UUID id = UUID.randomUUID();
        UUID seeker = UUID.randomUUID();
        JobSearchFilter filter = mock(JobSearchFilter.class);
        when(filter.page()).thenReturn(0);
        when(filter.size()).thenReturn(20);
        when(provider.search(filter)).thenReturn(new SearchProvider.SearchPage(List.of(job(id)), 1));
        when(provider.flags(seeker, List.of(id))).thenReturn(Map.of(id, new SearchProvider.Flags(true, true)));

        var page = service.search(filter, seeker);

        assertThat(page.items().get(0).saved()).isTrue();
        assertThat(page.items().get(0).applied()).isTrue();
    }
}
