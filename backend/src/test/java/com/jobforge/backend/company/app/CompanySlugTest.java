package com.jobforge.backend.company.app;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CompanySlugTest {

    @Test
    void slugifiesNamesAndStripsAccents() {
        assertThat(CompanyService.slugify("Acme Labs")).isEqualTo("acme-labs");
        assertThat(CompanyService.slugify("  Caf\u00e9 & Co.  ")).isEqualTo("cafe-co");
    }

    @Test
    void fallsBackWhenNothingUsable() {
        assertThat(CompanyService.slugify("!!!")).isEqualTo("company");
        assertThat(CompanyService.slugify(null)).isEqualTo("company");
    }

    @Test
    void capsLengthWithoutTrailingDash() {
        String slug = CompanyService.slugify("a".repeat(139) + " b" + "c".repeat(50));
        assertThat(slug.length()).isLessThanOrEqualTo(140);
        assertThat(slug).doesNotEndWith("-");
    }
}
