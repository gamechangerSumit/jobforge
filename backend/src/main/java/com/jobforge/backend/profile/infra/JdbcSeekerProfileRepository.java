package com.jobforge.backend.profile.infra;

import com.jobforge.backend.profile.app.SeekerProfileRepository;
import com.jobforge.backend.profile.app.SeekerProfileUpdate;
import com.jobforge.backend.profile.domain.ExpectedSalary;
import com.jobforge.backend.profile.domain.Links;
import com.jobforge.backend.profile.domain.Location;
import com.jobforge.backend.profile.domain.ProfileVisibility;
import com.jobforge.backend.profile.domain.SalaryPeriod;
import com.jobforge.backend.profile.domain.SeekerProfile;
import com.jobforge.backend.profile.domain.SkillEntry;
import com.jobforge.backend.profile.domain.SkillProficiency;
import com.jobforge.backend.shared.persistence.Db;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcSeekerProfileRepository implements SeekerProfileRepository {

    private final JdbcClient jdbc;

    public JdbcSeekerProfileRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<SeekerProfile> findByUserId(UUID userId) {
        Optional<SeekerProfile> profile = jdbc.sql("SELECT * FROM core.seeker_profiles WHERE user_id = :uid")
                .param("uid", userId).query(JdbcSeekerProfileRepository::map).optional();
        return profile.map(p -> withSkills(p, loadSkills(p.id())));
    }

    @Override
    public void insertEmpty(UUID id, UUID userId, Instant now) {
        jdbc.sql("""
                INSERT INTO core.seeker_profiles (id, user_id, version, created_at, updated_at)
                VALUES (:id, :uid, 0, :now, :now)
                """).param("id", id).param("uid", userId).param("now", Db.ts(now)).update();
    }

    @Override
    public void recalculateCompleteness(UUID profileId) {
        jdbc.sql("""
                UPDATE core.seeker_profiles p SET completeness_score =
                      (CASE WHEN coalesce(btrim(p.headline), '') <> '' THEN 15 ELSE 0 END)
                    + (CASE WHEN char_length(coalesce(btrim(p.summary), '')) >= 50 THEN 15 ELSE 0 END)
                    + (CASE WHEN p.location_city IS NOT NULL OR p.location_country IS NOT NULL THEN 10 ELSE 0 END)
                    + (CASE WHEN coalesce(btrim(p.current_title), '') <> '' THEN 10 ELSE 0 END)
                    + (CASE WHEN (SELECT count(*) FROM core.seeker_skills s WHERE s.seeker_profile_id = p.id) >= 3 THEN 20 ELSE 0 END)
                    + (CASE WHEN EXISTS (SELECT 1 FROM core.education e WHERE e.seeker_profile_id = p.id) THEN 10 ELSE 0 END)
                    + (CASE WHEN EXISTS (SELECT 1 FROM core.experience x WHERE x.seeker_profile_id = p.id) THEN 10 ELSE 0 END)
                    + (CASE WHEN EXISTS (SELECT 1 FROM core.resumes r WHERE r.seeker_profile_id = p.id
                                          AND r.is_primary AND r.deleted_at IS NULL) THEN 10 ELSE 0 END)
                 WHERE p.id = :id
                """).param("id", profileId).update();
    }

    @Override
    public void scrub(UUID userId, Instant now) {
        jdbc.sql("""
                UPDATE core.seeker_profiles
                   SET headline = NULL, summary = NULL, phone = NULL, location_city = NULL, location_state = NULL,
                       location_country = NULL, current_title = NULL, linkedin_url = NULL, github_url = NULL,
                       portfolio_url = NULL, open_to_work = false, visibility = 'PRIVATE', completeness_score = 0,
                       version = version + 1, updated_at = :now
                 WHERE user_id = :uid
                """).param("now", Db.ts(now)).param("uid", userId).update();
    }

    @Override
    public boolean replace(UUID id, SeekerProfileUpdate u, long expectedVersion, Instant now) {
        Location loc = u.location();
        ExpectedSalary sal = u.expectedSalary();
        Links links = u.links();
        return jdbc.sql("""
                UPDATE core.seeker_profiles SET
                  headline = :headline, summary = :summary, phone = :phone,
                  location_city = :city, location_state = :state, location_country = :country,
                  current_title = :title, years_experience = :years,
                  expected_salary_min = :smin, expected_salary_max = :smax,
                  expected_salary_currency = :scur, expected_salary_period = :sper,
                  notice_period_days = :notice, open_to_work = :otw, visibility = :vis,
                  linkedin_url = :li, github_url = :gh, portfolio_url = :pf,
                  version = version + 1, updated_at = :now
                WHERE id = :id AND version = :ver
                """)
                .param("headline", u.headline()).param("summary", u.summary()).param("phone", u.phone())
                .param("city", loc == null ? null : loc.city()).param("state", loc == null ? null : loc.state())
                .param("country", loc == null ? null : loc.country())
                .param("title", u.currentTitle()).param("years", u.yearsExperience())
                .param("smin", sal == null ? null : sal.min()).param("smax", sal == null ? null : sal.max())
                .param("scur", sal == null ? null : sal.currency())
                .param("sper", sal == null || sal.period() == null ? null : sal.period().name())
                .param("notice", u.noticePeriodDays()).param("otw", u.openToWork()).param("vis", u.visibility().name())
                .param("li", links == null ? null : links.linkedin()).param("gh", links == null ? null : links.github())
                .param("pf", links == null ? null : links.portfolio())
                .param("now", Db.ts(now)).param("id", id).param("ver", expectedVersion)
                .update() == 1;
    }

    private List<SkillEntry> loadSkills(UUID profileId) {
        return jdbc.sql("""
                SELECT s.name AS skill, ss.proficiency, ss.years_experience
                  FROM core.seeker_skills ss JOIN core.skills s ON s.id = ss.skill_id
                 WHERE ss.seeker_profile_id = :pid ORDER BY s.name
                """).param("pid", profileId)
                .query((rs, n) -> new SkillEntry(rs.getString("skill"),
                        SkillProficiency.valueOf(rs.getString("proficiency")), rs.getBigDecimal("years_experience")))
                .list();
    }

    private static SeekerProfile withSkills(SeekerProfile p, List<SkillEntry> skills) {
        return new SeekerProfile(p.id(), p.userId(), p.headline(), p.summary(), p.phone(), p.location(),
                p.currentTitle(), p.yearsExperience(), p.expectedSalary(), p.noticePeriodDays(), p.openToWork(),
                p.visibility(), p.links(), p.completenessScore(), skills, p.version(), p.updatedAt());
    }

    private static SeekerProfile map(ResultSet rs, int row) throws SQLException {
        Location location = new Location(rs.getString("location_city"), rs.getString("location_state"),
                trimmed(rs.getString("location_country")));
        BigDecimal min = rs.getBigDecimal("expected_salary_min");
        BigDecimal max = rs.getBigDecimal("expected_salary_max");
        String period = rs.getString("expected_salary_period");
        ExpectedSalary salary = new ExpectedSalary(min == null ? null : min.longValue(), max == null ? null : max.longValue(),
                trimmed(rs.getString("expected_salary_currency")), period == null ? null : SalaryPeriod.valueOf(period));
        Object notice = rs.getObject("notice_period_days");
        return new SeekerProfile(Db.uuid(rs, "id"), Db.uuid(rs, "user_id"), rs.getString("headline"),
                rs.getString("summary"), rs.getString("phone"), location.allNull() ? null : location,
                rs.getString("current_title"), rs.getBigDecimal("years_experience"), salary.allNull() ? null : salary,
                notice == null ? null : ((Number) notice).intValue(), rs.getBoolean("open_to_work"),
                ProfileVisibility.valueOf(rs.getString("visibility")),
                new Links(rs.getString("linkedin_url"), rs.getString("github_url"), rs.getString("portfolio_url")),
                rs.getInt("completeness_score"), List.of(), rs.getLong("version"), Db.instant(rs, "updated_at"));
    }

    private static String trimmed(String value) {
        return value == null ? null : value.trim(); // char(n) columns are blank-padded
    }
}
