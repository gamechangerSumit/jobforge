package com.jobforge.backend.search.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record JobSearchFilter(
        String query,
        String location,
        String country,
        String workMode,
        String employmentType,
        String experienceLevel,
        BigDecimal salaryMin,
        BigDecimal salaryMax,
        String currency,
        List<String> skills,
        UUID companyId,
        Instant postedAfter,
        JobSearchSort sort,
        int page,
        int size
) {
}