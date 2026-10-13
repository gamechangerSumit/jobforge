package com.jobforge.backend.community.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** NOT RUN in the authoring session. */
class TagsTest {

    @Test
    void normalizesCaseAndTrimsWhitespace() {
        assertThat(Tags.normalize("  Spring-Boot ")).isEqualTo("spring-boot");
    }

    @Test
    void rejectsInvalidSlugs() {
        assertThat(Tags.normalize("a")).isNull();
        assertThat(Tags.normalize("has space")).isNull();
        assertThat(Tags.normalize("emoji🙂")).isNull();
        assertThat(Tags.normalize("x".repeat(31))).isNull();
        assertThat(Tags.normalize(null)).isNull();
    }

    @Test
    void normalizeAllDeduplicatesAndReportsInvalidEntries() {
        List<String> invalid = new ArrayList<>();
        assertThat(Tags.normalizeAll(List.of("Java", "java", "bad tag", "sql"), invalid)).containsExactly("java", "sql");
        assertThat(invalid).containsExactly("bad tag");
    }
}
