package com.jobforge.backend.search.infra;

import com.jobforge.backend.search.app.SearchProvider;
import com.jobforge.backend.search.domain.JobFacets;
import com.jobforge.backend.search.domain.JobFacets.Bucket;
import com.jobforge.backend.search.domain.JobSearchFilter;
import com.jobforge.backend.search.domain.JobSearchResult;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * PostgreSQL implementation (ARCHITECTURE 15): FTS on jobs.search_vector, pg_trgm title fuzziness, indexed filters,
 * AND-semantics skills filter. Only PUBLISHED, non-deleted jobs are ever visible. All values are bound parameters;
 * the only interpolated SQL fragments are constant strings chosen from enums.
 */
@Repository
public class JdbcSearchProvider implements SearchProvider {

    private static final String PROJECTION = """
            SELECT j.id, j.title,
                   c.id AS company_id, c.name AS company_name, c.slug AS company_slug,
                   c.logo_key AS company_logo_key, (c.verification_status = 'VERIFIED') AS company_verified,
                   j.location_city, j.location_state, j.location_country::text AS location_country,
                   j.work_mode, j.employment_type, j.experience_level,
                   j.salary_min, j.salary_max, j.salary_currency::text AS salary_currency, j.salary_period,
                   j.published_at,
                   ARRAY(SELECT s.name::text FROM core.job_skills js JOIN core.skills s ON s.id = js.skill_id
                          WHERE js.job_id = j.id ORDER BY s.name::text) AS skills,
            """;

    private static final String FROM_JOBS = " FROM core.jobs j JOIN core.companies c ON c.id = j.company_id ";

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcSearchProvider(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public java.util.Map<UUID, Flags> flags(UUID seekerUserId, java.util.Collection<UUID> jobIds) {
        if (jobIds.isEmpty()) {
            return java.util.Map.of();
        }
        MapSqlParameterSource p = new MapSqlParameterSource().addValue("seeker", seekerUserId).addValue("ids", jobIds);
        java.util.Map<UUID, Flags> out = new java.util.HashMap<>();
        jdbc.query("""
                SELECT j.id,
                       EXISTS (SELECT 1 FROM core.saved_jobs s WHERE s.job_id = j.id AND s.seeker_user_id = :seeker) AS saved,
                       EXISTS (SELECT 1 FROM core.applications a WHERE a.job_id = j.id AND a.seeker_user_id = :seeker
                               AND a.status <> 'WITHDRAWN') AS applied
                  FROM core.jobs j WHERE j.id IN (:ids)
                """, p, rs -> {
            out.put(rs.getObject("id", UUID.class), new Flags(rs.getBoolean("saved"), rs.getBoolean("applied")));
        });
        return out;
    }

    /** WHERE clause + parameters for a filter; the same builder serves results, counts and facets. */
    private static final class Criteria {
        final List<String> predicates = new ArrayList<>(List.of("j.deleted_at IS NULL", "j.status = 'PUBLISHED'"));
        final MapSqlParameterSource params = new MapSqlParameterSource();
        boolean hasQuery;

        String where() {
            return String.join(" AND ", predicates);
        }
    }

    private static Criteria criteria(JobSearchFilter f) {
        Criteria c = new Criteria();
        if (f.query() != null && !f.query().isBlank()) {
            c.hasQuery = true;
            c.predicates.add("(j.search_vector @@ websearch_to_tsquery('english', unaccent(:query))"
                    + " OR similarity(unaccent(j.title), unaccent(:query)) >= 0.20"
                    + " OR similarity(unaccent(c.name), unaccent(:query)) >= 0.40)");
            c.params.addValue("query", f.query());
        }
        if (f.location() != null && !f.location().isBlank()) {
            c.predicates.add("unaccent(concat_ws(' ', j.location_city, j.location_state, j.location_country))"
                    + " ILIKE unaccent(:location) ESCAPE '\\'");
            c.params.addValue("location", "%" + escapeLike(f.location().trim()) + "%");
        }
        if (f.country() != null) {
            c.predicates.add("j.location_country = :country");
            c.params.addValue("country", f.country());
        }
        if (f.workMode() != null) {
            c.predicates.add("j.work_mode = :workMode");
            c.params.addValue("workMode", f.workMode());
        }
        if (f.employmentType() != null) {
            c.predicates.add("j.employment_type = :employmentType");
            c.params.addValue("employmentType", f.employmentType());
        }
        if (f.experienceLevel() != null) {
            c.predicates.add("j.experience_level = :experienceLevel");
            c.params.addValue("experienceLevel", f.experienceLevel());
        }
        if (f.companyId() != null) {
            c.predicates.add("j.company_id = :companyId");
            c.params.addValue("companyId", f.companyId());
        }
        if (f.salaryMin() != null) {
            c.predicates.add("COALESCE(j.salary_max, j.salary_min, 0) >= :salaryMin");
            c.params.addValue("salaryMin", f.salaryMin());
        }
        if (f.salaryMax() != null) {
            c.predicates.add("COALESCE(j.salary_min, j.salary_max, 0) <= :salaryMax");
            c.params.addValue("salaryMax", f.salaryMax());
        }
        if (f.currency() != null) {
            c.predicates.add("j.salary_currency = :currency");
            c.params.addValue("currency", f.currency());
        }
        if (f.postedAfter() != null) {
            c.predicates.add("j.published_at >= :postedAfter");
            c.params.addValue("postedAfter", java.sql.Timestamp.from(f.postedAfter()));
        }
        if (f.skills() != null && !f.skills().isEmpty()) {
            List<String> lower = f.skills().stream().map(s -> s.toLowerCase(java.util.Locale.ROOT)).distinct().toList();
            c.predicates.add("j.id IN (SELECT js.job_id FROM core.job_skills js JOIN core.skills s ON s.id = js.skill_id"
                    + " WHERE lower(s.name::text) IN (:skills) GROUP BY js.job_id"
                    + " HAVING COUNT(DISTINCT lower(s.name::text)) = :skillCount)");
            c.params.addValue("skills", lower);
            c.params.addValue("skillCount", (long) lower.size());
        }
        return c;
    }

    private static String escapeLike(String q) {
        return q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    @Override
    public SearchPage search(JobSearchFilter f) {
        Criteria c = criteria(f);
        String relevance = c.hasQuery
                ? "(ts_rank_cd(j.search_vector, websearch_to_tsquery('english', unaccent(:query)))"
                        + " + similarity(unaccent(j.title), unaccent(:query)))"
                : "0.0";
        String orderBy = switch (f.sort()) {
            case RELEVANCE -> relevance + " DESC, j.published_at DESC, j.id DESC";
            case POSTED_AT -> "j.published_at DESC, j.id DESC";
            case SALARY -> "COALESCE(j.salary_max, j.salary_min, 0) DESC, j.published_at DESC, j.id DESC";
        };
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + FROM_JOBS + "WHERE " + c.where(), c.params, Long.class);
        c.params.addValue("limit", f.size());
        c.params.addValue("offset", f.page() * f.size());
        String sql = PROJECTION + " " + relevance + " AS relevance" + FROM_JOBS + "WHERE " + c.where()
                + " ORDER BY " + orderBy + " LIMIT :limit OFFSET :offset";
        List<JobSearchResult> items = jdbc.query(sql, c.params, JdbcSearchProvider::map);
        return new SearchPage(items, total == null ? 0 : total);
    }

    @Override
    public JobFacets facets(JobSearchFilter f) {
        Criteria c = criteria(f);
        String base = FROM_JOBS + "WHERE " + c.where();
        return new JobFacets(
                buckets("SELECT j.work_mode AS v, count(*) AS n" + base + " GROUP BY j.work_mode ORDER BY n DESC, v", c),
                buckets("SELECT j.employment_type AS v, count(*) AS n" + base
                        + " GROUP BY j.employment_type ORDER BY n DESC, v", c),
                buckets("SELECT j.experience_level AS v, count(*) AS n" + base
                        + " GROUP BY j.experience_level ORDER BY n DESC, v", c),
                buckets("SELECT concat_ws(', ', j.location_city, j.location_country::text) AS v, count(*) AS n" + base
                        + " AND j.location_country IS NOT NULL GROUP BY 1 ORDER BY n DESC, v LIMIT 10", c),
                buckets("SELECT s.name::text AS v, count(DISTINCT j.id) AS n" + base.replace(
                        FROM_JOBS, FROM_JOBS + " JOIN core.job_skills fjs ON fjs.job_id = j.id"
                                + " JOIN core.skills s ON s.id = fjs.skill_id ")
                        + " GROUP BY s.name::text ORDER BY n DESC, v LIMIT 15", c));
    }

    private List<Bucket> buckets(String sql, Criteria c) {
        return jdbc.query(sql, c.params, (rs, n) -> new Bucket(rs.getString("v"), rs.getLong("n")));
    }

    @Override
    public Optional<List<JobSearchResult>> similar(UUID jobId, int limit) {
        MapSqlParameterSource p = new MapSqlParameterSource("id", jobId).addValue("limit", limit);
        Boolean visible = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM core.jobs WHERE id = :id"
                + " AND status = 'PUBLISHED' AND deleted_at IS NULL)", p, Boolean.class);
        if (!Boolean.TRUE.equals(visible)) {
            return Optional.empty();
        }
        String sql = PROJECTION + """
                 similarity(j.title, (SELECT title FROM core.jobs WHERE id = :id)) AS relevance
                """ + FROM_JOBS + """
                 WHERE j.deleted_at IS NULL AND j.status = 'PUBLISHED' AND j.id <> :id
                   AND (EXISTS (SELECT 1 FROM core.job_skills a JOIN core.job_skills b ON b.skill_id = a.skill_id
                                 WHERE a.job_id = j.id AND b.job_id = :id)
                        OR similarity(j.title, (SELECT title FROM core.jobs WHERE id = :id)) >= 0.30)
                 ORDER BY (SELECT count(*) FROM core.job_skills a JOIN core.job_skills b ON b.skill_id = a.skill_id
                            WHERE a.job_id = j.id AND b.job_id = :id) DESC,
                          relevance DESC, j.published_at DESC, j.id DESC
                 LIMIT :limit
                """;
        return Optional.of(jdbc.query(sql, p, JdbcSearchProvider::map));
    }

    private static JobSearchResult map(ResultSet rs, int row) throws SQLException {
        UUID companyId = rs.getObject("company_id", UUID.class);
        String logoKey = rs.getString("company_logo_key");
        String logoUrl = logoKey == null ? null
                : com.jobforge.backend.shared.config.ApiPaths.BASE + "/companies/" + companyId + "/logo?v="
                        + Integer.toHexString(logoKey.hashCode());
        JobSearchResult.CompanySummary company = new JobSearchResult.CompanySummary(
                companyId, rs.getString("company_name"), rs.getString("company_slug"),
                logoUrl, rs.getBoolean("company_verified"));
        JobSearchResult.LocationSummary location = new JobSearchResult.LocationSummary(rs.getString("location_city"),
                rs.getString("location_state"), rs.getString("location_country"));
        BigDecimal min = rs.getBigDecimal("salary_min");
        BigDecimal max = rs.getBigDecimal("salary_max");
        String currency = rs.getString("salary_currency");
        String period = rs.getString("salary_period");
        JobSearchResult.SalarySummary salary = min != null || max != null || currency != null || period != null
                ? new JobSearchResult.SalarySummary(min, max, currency, period) : null;
        java.sql.Array array = rs.getArray("skills");
        List<String> skills = array == null ? List.of() : List.of((String[]) array.getArray());
        java.sql.Timestamp posted = rs.getTimestamp("published_at");
        return new JobSearchResult(rs.getObject("id", UUID.class), rs.getString("title"), company, location,
                rs.getString("work_mode"), rs.getString("employment_type"), rs.getString("experience_level"), salary,
                skills, posted == null ? null : posted.toInstant(), null, null, rs.getDouble("relevance"));
    }
}
