package com.jobforge.backend.job.infra;

import com.jobforge.backend.job.app.JobRepository;
import com.jobforge.backend.job.domain.EmploymentType;
import com.jobforge.backend.job.domain.ExperienceLevel;
import com.jobforge.backend.job.domain.Job;
import com.jobforge.backend.job.domain.JobContent;
import com.jobforge.backend.job.domain.JobLocation;
import com.jobforge.backend.job.domain.JobSalary;
import com.jobforge.backend.job.domain.JobSkill;
import com.jobforge.backend.job.domain.JobStatus;
import com.jobforge.backend.job.domain.SalaryPeriod;
import com.jobforge.backend.job.domain.WorkMode;
import com.jobforge.backend.shared.api.PagedResult;
import com.jobforge.backend.shared.persistence.Db;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcJobRepository implements JobRepository {

    private static final String COLUMNS = """
            id, company_id, created_by, title, slug, description, requirements, benefits, employment_type, work_mode,
            experience_level, location_city, location_state, location_country, salary_min, salary_max, salary_currency,
            salary_period, salary_visible, openings, status, published_at, expires_at, closed_at, ai_generated,
            ai_request_id, quality_score, removed_by, removed_reason, version, created_at, updated_at
            """;

    private final JdbcClient jdbc;

    public JdbcJobRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------ writes

    @Override
    public void insert(Job job, Instant now) {
        JobContent c = job.content();
        jdbc.sql("""
                INSERT INTO core.jobs
                  (id, company_id, created_by, title, slug, description, requirements, benefits, employment_type,
                   work_mode, experience_level, location_city, location_state, location_country, salary_min, salary_max,
                   salary_currency, salary_period, salary_visible, openings, status, published_at, expires_at, closed_at,
                   ai_generated, ai_request_id, quality_score, version, created_at, updated_at)
                VALUES
                  (:id, :company, :creator, :title, :slug, :description, :requirements, :benefits, :et, :wm, :el,
                   :city, :state, :country, :smin, :smax, :scur, :sper, :svis, :openings, :status, :published, :expires,
                   :closed, :ai, :aiReq, :quality, 0, :now, :now)
                """)
                .param("id", job.id()).param("company", job.companyId()).param("creator", job.createdBy())
                .param("title", c.title()).param("slug", job.slug()).param("description", c.description())
                .param("requirements", c.requirements()).param("benefits", c.benefits())
                .param("et", c.employmentType().name()).param("wm", c.workMode().name())
                .param("el", c.experienceLevel().name())
                .param("city", city(c)).param("state", state(c)).param("country", country(c))
                .param("smin", c.salary() == null ? null : c.salary().min())
                .param("smax", c.salary() == null ? null : c.salary().max())
                .param("scur", c.salary() == null ? null : c.salary().currency())
                .param("sper", c.salary() == null || c.salary().period() == null ? null : c.salary().period().name())
                .param("svis", c.salaryVisible()).param("openings", c.openings()).param("status", job.status().name())
                .param("published", Db.ts(job.publishedAt())).param("expires", Db.ts(c.expiresAt()))
                .param("closed", Db.ts(job.closedAt())).param("ai", job.aiGenerated()).param("aiReq", job.aiRequestId())
                .param("quality", job.qualityScore()).param("now", Db.ts(now))
                .update();
        insertSkills(job.id(), c.skills());
    }

    @Override
    public boolean update(Job job, long expectedVersion, Instant now, boolean replaceSkills) {
        JobContent c = job.content();
        int rows = jdbc.sql("""
                UPDATE core.jobs SET
                  title = :title, description = :description, requirements = :requirements, benefits = :benefits,
                  employment_type = :et, work_mode = :wm, experience_level = :el,
                  location_city = :city, location_state = :state, location_country = :country,
                  salary_min = :smin, salary_max = :smax, salary_currency = :scur, salary_period = :sper,
                  salary_visible = :svis, openings = :openings, status = :status, published_at = :published,
                  expires_at = :expires, closed_at = :closed, removed_by = :removedBy, removed_reason = :removedReason,
                  version = version + 1, updated_at = :now
                WHERE id = :id AND version = :ver AND deleted_at IS NULL
                """)
                .param("title", c.title()).param("description", c.description())
                .param("requirements", c.requirements()).param("benefits", c.benefits())
                .param("et", c.employmentType().name()).param("wm", c.workMode().name())
                .param("el", c.experienceLevel().name())
                .param("city", city(c)).param("state", state(c)).param("country", country(c))
                .param("smin", c.salary() == null ? null : c.salary().min())
                .param("smax", c.salary() == null ? null : c.salary().max())
                .param("scur", c.salary() == null ? null : c.salary().currency())
                .param("sper", c.salary() == null || c.salary().period() == null ? null : c.salary().period().name())
                .param("svis", c.salaryVisible()).param("openings", c.openings()).param("status", job.status().name())
                .param("published", Db.ts(job.publishedAt())).param("expires", Db.ts(c.expiresAt()))
                .param("closed", Db.ts(job.closedAt())).param("removedBy", job.removedBy())
                .param("removedReason", job.removedReason()).param("now", Db.ts(now))
                .param("id", job.id()).param("ver", expectedVersion)
                .update();
        if (rows != 1) {
            return false;
        }
        if (replaceSkills) {
            jdbc.sql("DELETE FROM core.job_skills WHERE job_id = :id").param("id", job.id()).update();
            insertSkills(job.id(), c.skills());
        }
        return true;
    }

    @Override
    public boolean softDelete(UUID id, long expectedVersion, Instant now) {
        return jdbc.sql("""
                UPDATE core.jobs SET deleted_at = :now, version = version + 1, updated_at = :now
                 WHERE id = :id AND version = :ver AND deleted_at IS NULL
                """).param("now", Db.ts(now)).param("id", id).param("ver", expectedVersion).update() == 1;
    }

    @Override
    public List<ExpiredJob> expireDue(Instant now) {
        return jdbc.sql("""
                UPDATE core.jobs SET status = 'EXPIRED', version = version + 1, updated_at = :now
                 WHERE status = 'PUBLISHED' AND deleted_at IS NULL AND expires_at IS NOT NULL AND expires_at <= :now
                RETURNING id, company_id
                """).param("now", Db.ts(now))
                .query((rs, n) -> new ExpiredJob(Db.uuid(rs, "id"), Db.uuid(rs, "company_id"))).list();
    }

    private void insertSkills(UUID jobId, List<JobSkill> skills) {
        for (JobSkill skill : skills) {
            jdbc.sql("INSERT INTO core.job_skills (job_id, skill_id, is_required) VALUES (:job, :skill, :req)")
                    .param("job", jobId).param("skill", skill.skillId()).param("req", skill.required()).update();
        }
    }

    // ------------------------------------------------------------------ reads

    @Override
    public Optional<Job> findById(UUID id) {
        List<Job> jobs = jdbc.sql("SELECT " + COLUMNS + " FROM core.jobs WHERE id = :id AND deleted_at IS NULL")
                .param("id", id).query(JdbcJobRepository::map).list();
        return withSkills(jobs).stream().findFirst();
    }

    @Override
    public List<Job> findByIds(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return withSkills(jdbc.sql("SELECT " + COLUMNS + " FROM core.jobs WHERE id IN (:ids) AND deleted_at IS NULL")
                .param("ids", ids).query(JdbcJobRepository::map).list());
    }

    @Override
    public List<UUID> findIdsByCompany(UUID companyId) {
        return jdbc.sql("SELECT id FROM core.jobs WHERE company_id = :cid AND deleted_at IS NULL")
                .param("cid", companyId).query((rs, n) -> Db.uuid(rs, "id")).list();
    }

    @Override
    public PagedResult<Job> findByCompany(UUID companyId, JobStatus status, String q, int page, int size) {
        Map<String, Object> params = new HashMap<>();
        StringBuilder where = new StringBuilder("deleted_at IS NULL AND company_id = :cid");
        params.put("cid", companyId);
        appendFilters(where, params, status, q);
        return page(where.toString(), params, " ORDER BY updated_at DESC, id DESC", page, size);
    }

    @Override
    public PagedResult<Job> adminSearch(JobStatus status, String q, UUID companyId, int page, int size) {
        Map<String, Object> params = new HashMap<>();
        StringBuilder where = new StringBuilder("deleted_at IS NULL");
        if (companyId != null) {
            where.append(" AND company_id = :cid");
            params.put("cid", companyId);
        }
        appendFilters(where, params, status, q);
        return page(where.toString(), params, " ORDER BY updated_at DESC, id DESC", page, size);
    }

    private static void appendFilters(StringBuilder where, Map<String, Object> params, JobStatus status, String q) {
        if (status != null) {
            where.append(" AND status = :status");
            params.put("status", status.name());
        }
        if (q != null && !q.isBlank()) {
            where.append(" AND title ILIKE :q ESCAPE '\\'");
            params.put("q", "%" + q.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
        }
    }

    private PagedResult<Job> page(String where, Map<String, Object> params, String orderBy, int page, int size) {
        Long total = jdbc.sql("SELECT count(*) FROM core.jobs WHERE " + where).params(params).query(Long.class).single();
        Map<String, Object> pageParams = new LinkedHashMap<>(params);
        pageParams.put("limit", size);
        pageParams.put("offset", (long) page * size);
        List<Job> jobs = jdbc.sql("SELECT " + COLUMNS + " FROM core.jobs WHERE " + where + orderBy
                        + " LIMIT :limit OFFSET :offset")
                .params(pageParams).query(JdbcJobRepository::map).list();
        return new PagedResult<>(withSkills(jobs), total);
    }

    private List<Job> withSkills(List<Job> jobs) {
        if (jobs.isEmpty()) {
            return jobs;
        }
        Map<UUID, List<JobSkill>> byJob = new HashMap<>();
        jdbc.sql("SELECT job_id, skill_id, is_required FROM core.job_skills WHERE job_id IN (:ids) ORDER BY skill_id")
                .param("ids", jobs.stream().map(Job::id).toList())
                .query((rs, n) -> Map.entry(Db.uuid(rs, "job_id"), new JobSkill(Db.uuid(rs, "skill_id"), rs.getBoolean("is_required"))))
                .list().forEach(e -> byJob.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(e.getValue()));
        return jobs.stream().map(j -> j.withContent(j.content().withSkills(byJob.getOrDefault(j.id(), List.of())))).toList();
    }

    // ------------------------------------------------------------------ mapping

    private static String city(JobContent c) {
        return c.location() == null ? null : c.location().city();
    }

    private static String state(JobContent c) {
        return c.location() == null ? null : c.location().state();
    }

    private static String country(JobContent c) {
        return c.location() == null ? null : c.location().country();
    }

    private static Job map(ResultSet rs, int row) throws SQLException {
        JobLocation location = new JobLocation(rs.getString("location_city"), rs.getString("location_state"),
                rs.getString("location_country"));
        BigDecimal min = rs.getBigDecimal("salary_min");
        BigDecimal max = rs.getBigDecimal("salary_max");
        String period = rs.getString("salary_period");
        JobSalary salary = new JobSalary(min == null ? null : min.longValue(), max == null ? null : max.longValue(),
                rs.getString("salary_currency"), period == null ? null : SalaryPeriod.valueOf(period));
        Object quality = rs.getObject("quality_score");
        JobContent content = new JobContent(rs.getString("title"), rs.getString("description"),
                rs.getString("requirements"), rs.getString("benefits"),
                EmploymentType.valueOf(rs.getString("employment_type")), WorkMode.valueOf(rs.getString("work_mode")),
                ExperienceLevel.valueOf(rs.getString("experience_level")), location.allNull() ? null : location,
                salary.allNull() ? null : salary, rs.getBoolean("salary_visible"), rs.getInt("openings"),
                Db.instant(rs, "expires_at"), List.of());
        return new Job(Db.uuid(rs, "id"), Db.uuid(rs, "company_id"), Db.uuid(rs, "created_by"), rs.getString("slug"),
                content, JobStatus.valueOf(rs.getString("status")), Db.instant(rs, "published_at"),
                Db.instant(rs, "closed_at"), rs.getBoolean("ai_generated"), Db.uuid(rs, "ai_request_id"),
                quality == null ? null : ((Number) quality).intValue(), Db.uuid(rs, "removed_by"),
                rs.getString("removed_reason"), rs.getLong("version"), Db.instant(rs, "created_at"),
                Db.instant(rs, "updated_at"));
    }
}
