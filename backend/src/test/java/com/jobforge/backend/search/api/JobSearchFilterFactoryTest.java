package com.jobforge.backend.search.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobforge.backend.search.domain.JobSearchFilter;
import com.jobforge.backend.search.domain.JobSearchSort;
import com.jobforge.backend.shared.error.ValidationFailedException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class JobSearchFilterFactoryTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-08T00:00:00Z"), ZoneOffset.UTC);

    private static MockHttpServletRequest request(String... kv) {
        MockHttpServletRequest r = new MockHttpServletRequest();
        for (int i = 0; i < kv.length; i += 2) {
            r.addParameter(kv[i], kv[i + 1]);
        }
        return r;
    }

    @Test
    void defaultsToNewestFirstWithoutQuery() {
        JobSearchFilter f = JobSearchFilterFactory.from(request(), CLOCK, true);
        assertThat(f.sort()).isEqualTo(JobSearchSort.POSTED_AT);
        assertThat(f.page()).isZero();
    }

    @Test
    void usesRelevanceWhenQueryPresent() {
        assertThat(JobSearchFilterFactory.from(request("q", "java"), CLOCK, true).sort())
                .isEqualTo(JobSearchSort.RELEVANCE);
    }

    @Test
    void combinesRepeatedSkillParametersCaseInsensitively() {
        JobSearchFilter f = JobSearchFilterFactory.from(request("skills", "Java", "skills", "kafka", "skills", "JAVA"), CLOCK, true);
        assertThat(f.skills()).containsExactly("java", "kafka");
    }

    @Test
    void computesPostedWithinFromInjectedClock() {
        JobSearchFilter f = JobSearchFilterFactory.from(request("postedWithin", "7d"), CLOCK, true);
        assertThat(f.postedAfter()).isEqualTo(Instant.parse("2026-10-01T00:00:00Z"));
    }

    @Test
    void rejectsInvalidValuesWithValidationErrors() {
        assertThatThrownBy(() -> JobSearchFilterFactory.from(request("workMode", "MOON"), CLOCK, true))
                .isInstanceOf(ValidationFailedException.class);
        assertThatThrownBy(() -> JobSearchFilterFactory.from(request("sort", "bogus"), CLOCK, true))
                .isInstanceOf(ValidationFailedException.class);
        assertThatThrownBy(() -> JobSearchFilterFactory.from(request("postedWithin", "1y"), CLOCK, true))
                .isInstanceOf(ValidationFailedException.class);
        assertThatThrownBy(() -> JobSearchFilterFactory.from(request("salaryMin", "9", "salaryMax", "1"), CLOCK, true))
                .isInstanceOf(ValidationFailedException.class);
    }

    @Test
    void rejectsUnknownParameters() {
        assertThatThrownBy(() -> JobSearchFilterFactory.from(request("nope", "1"), CLOCK, true))
                .isInstanceOf(RuntimeException.class);
    }
}
