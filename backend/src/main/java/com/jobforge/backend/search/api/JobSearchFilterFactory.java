package com.jobforge.backend.search.api;

import com.jobforge.backend.search.domain.JobSearchFilter;
import com.jobforge.backend.search.domain.JobSearchSort;
import com.jobforge.backend.shared.error.ApiException;
import com.jobforge.backend.shared.error.ErrorCode;
import com.jobforge.backend.shared.error.ValidationFailedException;
import com.jobforge.backend.shared.web.QueryParams;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Parses and validates the public job search query string (API_CONTRACT 6-8, 12.4): unknown parameters and invalid
 * enum/sort/postedWithin values are 400 VALIDATION_FAILED instead of silently ignored or surfacing as 500.
 */
final class JobSearchFilterFactory {

    private static final Set<String> WORK_MODES = Set.of("ONSITE", "HYBRID", "REMOTE");
    private static final Set<String> EMPLOYMENT_TYPES = Set.of("FULL_TIME", "PART_TIME", "CONTRACT", "INTERNSHIP", "FREELANCE");
    private static final Set<String> LEVELS = Set.of("INTERN", "ENTRY", "MID", "SENIOR", "LEAD", "EXECUTIVE");
    private static final int MAX_SKILLS = 10;

    static final String[] FILTER_PARAMS = {"q", "location", "country", "workMode", "employmentType", "experienceLevel",
            "salaryMin", "salaryMax", "currency", "skills", "companyId", "postedWithin"};

    private JobSearchFilterFactory() {}

    static String[] withPaging(String... extra) {
        String[] all = Arrays.copyOf(FILTER_PARAMS, FILTER_PARAMS.length + extra.length);
        System.arraycopy(extra, 0, all, FILTER_PARAMS.length, extra.length);
        return all;
    }

    static JobSearchFilter from(HttpServletRequest request, Clock clock, boolean paged) {
        QueryParams p = new QueryParams(request, paged ? withPaging("page", "size", "sort") : FILTER_PARAMS);
        String q = p.string("q");
        if (q != null && q.length() > 200) {
            throw ValidationFailedException.of("q", "SIZE", "must be at most 200 characters");
        }
        String country = upperOrNull(p.string("country"));
        if (country != null && !country.matches("[A-Z]{2}")) {
            throw ValidationFailedException.of("country", "PATTERN", "must be an ISO 3166-1 alpha-2 code");
        }
        String currency = upperOrNull(p.string("currency"));
        if (currency != null && !currency.matches("[A-Z]{3}")) {
            throw ValidationFailedException.of("currency", "PATTERN", "must be an ISO 4217 code");
        }
        BigDecimal salaryMin = decimal(p.string("salaryMin"), "salaryMin");
        BigDecimal salaryMax = decimal(p.string("salaryMax"), "salaryMax");
        if (salaryMin != null && salaryMax != null && salaryMin.compareTo(salaryMax) > 0) {
            throw ValidationFailedException.of("salaryMax", "MIN", "must be greater than or equal to salaryMin");
        }
        String[] rawSkills = request.getParameterValues("skills");
        List<String> skills = parseSkills(rawSkills == null ? null : String.join(",", rawSkills));
        int page = paged ? p.page() : 0;
        int size = paged ? p.size() : 20;
        return new JobSearchFilter(q, p.string("location"), country,
                enumOrNull(p.string("workMode"), WORK_MODES, "workMode"),
                enumOrNull(p.string("employmentType"), EMPLOYMENT_TYPES, "employmentType"),
                enumOrNull(p.string("experienceLevel"), LEVELS, "experienceLevel"), salaryMin, salaryMax, currency,
                skills, p.uuid("companyId"), postedAfter(p.string("postedWithin"), clock),
                paged ? sort(request.getParameter("sort"), q) : JobSearchSort.POSTED_AT, page, size);
    }

    private static JobSearchSort sort(String raw, String q) {
        if (raw == null || raw.isBlank()) {
            return q == null ? JobSearchSort.POSTED_AT : JobSearchSort.RELEVANCE;
        }
        // Contract form is "field[,asc|desc]"; every whitelisted field has a single natural direction here.
        String field = raw.split(",")[0].trim().toLowerCase();
        return switch (field) {
            case "relevance" -> JobSearchSort.RELEVANCE;
            case "postedat" -> JobSearchSort.POSTED_AT;
            case "salary" -> JobSearchSort.SALARY;
            default -> throw ValidationFailedException.of("sort", "INVALID_ENUM", "must be one of relevance, postedAt, salary");
        };
    }

    private static List<String> parseSkills(String raw) {
        if (raw == null) {
            return List.of();
        }
        List<String> skills = Arrays.stream(raw.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .map(String::toLowerCase).distinct().toList();
        if (skills.size() > MAX_SKILLS) {
            throw ValidationFailedException.of("skills", "SIZE", "at most " + MAX_SKILLS + " skills can be combined");
        }
        return skills;
    }

    private static java.time.Instant postedAfter(String value, Clock clock) {
        if (value == null) {
            return null;
        }
        Duration d = switch (value) {
            case "24h" -> Duration.ofHours(24);
            case "7d" -> Duration.ofDays(7);
            case "30d" -> Duration.ofDays(30);
            default -> throw ValidationFailedException.of("postedWithin", "INVALID_ENUM", "must be one of 24h, 7d, 30d");
        };
        return clock.instant().minus(d);
    }

    private static String enumOrNull(String value, Set<String> allowed, String field) {
        if (value == null) {
            return null;
        }
        if (!allowed.contains(value)) {
            throw ValidationFailedException.of(field, "INVALID_ENUM", "must be one of the allowed values");
        }
        return value;
    }

    private static BigDecimal decimal(String value, String field) {
        if (value == null) {
            return null;
        }
        try {
            BigDecimal d = new BigDecimal(value);
            if (d.signum() < 0) {
                throw ValidationFailedException.of(field, "MIN", "must be greater than or equal to 0");
            }
            return d;
        } catch (NumberFormatException e) {
            throw new ApiException(ErrorCode.MALFORMED_REQUEST, "Parameter '" + field + "' must be a number.");
        }
    }

    private static String upperOrNull(String v) {
        return v == null ? null : v.toUpperCase();
    }
}
