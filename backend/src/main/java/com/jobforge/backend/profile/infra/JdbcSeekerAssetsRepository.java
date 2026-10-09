package com.jobforge.backend.profile.infra;

import com.jobforge.backend.profile.app.SeekerAssets.EducationInput;
import com.jobforge.backend.profile.app.SeekerAssets.EducationItem;
import com.jobforge.backend.profile.app.SeekerAssets.ExperienceInput;
import com.jobforge.backend.profile.app.SeekerAssets.ExperienceItem;
import com.jobforge.backend.profile.app.SeekerAssets.ResumeFile;
import com.jobforge.backend.profile.app.SeekerAssets.ResumeItem;
import com.jobforge.backend.profile.app.SeekerAssets.SkillItem;
import com.jobforge.backend.profile.app.SeekerAssetsRepository;
import com.jobforge.backend.shared.persistence.Db;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcSeekerAssetsRepository implements SeekerAssetsRepository {

    private final JdbcClient jdbc;

    public JdbcSeekerAssetsRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<UUID> profileIdOfUser(UUID userId) {
        return jdbc.sql("SELECT id FROM core.seeker_profiles WHERE user_id = :uid").param("uid", userId)
                .query(UUID.class).optional();
    }

    // ------------------------------------------------------------------ skills

    @Override
    public List<SkillItem> skills(UUID profileId) {
        return jdbc.sql("""
                SELECT s.name::text AS name, ss.proficiency, ss.years_experience
                  FROM core.seeker_skills ss JOIN core.skills s ON s.id = ss.skill_id
                 WHERE ss.seeker_profile_id = :pid ORDER BY lower(s.name::text)
                """).param("pid", profileId)
                .query((rs, n) -> new SkillItem(rs.getString("name"), rs.getString("proficiency"),
                        rs.getBigDecimal("years_experience")))
                .list();
    }

    @Override
    public void replaceSkills(UUID profileId, Map<String, UUID> skillIds, List<SkillItem> items) {
        jdbc.sql("DELETE FROM core.seeker_skills WHERE seeker_profile_id = :pid").param("pid", profileId).update();
        for (SkillItem item : items) {
            UUID skillId = skillIds.get(item.skill().trim().replaceAll("\\s+", " ").toLowerCase(java.util.Locale.ROOT));
            jdbc.sql("""
                    INSERT INTO core.seeker_skills (seeker_profile_id, skill_id, proficiency, years_experience)
                    VALUES (:pid, :sid, :prof, :years)
                    ON CONFLICT (seeker_profile_id, skill_id) DO NOTHING
                    """).param("pid", profileId).param("sid", skillId).param("prof", item.proficiency())
                    .param("years", item.years()).update();
        }
    }

    // --------------------------------------------------------------- education

    private static final String EDU_COLUMNS =
            "id, institution, degree, field_of_study, start_date, end_date, grade, description";

    private static EducationItem mapEducation(ResultSet rs, int n) throws SQLException {
        return new EducationItem(Db.uuid(rs, "id"), rs.getString("institution"), rs.getString("degree"),
                rs.getString("field_of_study"), rs.getObject("start_date", LocalDate.class),
                rs.getObject("end_date", LocalDate.class), rs.getString("grade"), rs.getString("description"));
    }

    @Override
    public List<EducationItem> education(UUID profileId) {
        return jdbc.sql("SELECT " + EDU_COLUMNS + " FROM core.education WHERE seeker_profile_id = :pid "
                + "ORDER BY start_date DESC, created_at DESC").param("pid", profileId)
                .query(JdbcSeekerAssetsRepository::mapEducation).list();
    }

    @Override
    public EducationItem insertEducation(UUID profileId, UUID id, EducationInput in) {
        return jdbc.sql("""
                INSERT INTO core.education (id, seeker_profile_id, institution, degree, field_of_study, start_date,
                                            end_date, grade, description)
                VALUES (:id, :pid, :inst, :deg, :field, :start, :end, :grade, :descr)
                RETURNING """ + " " + EDU_COLUMNS)
                .param("id", id).param("pid", profileId).param("inst", in.institution()).param("deg", in.degree())
                .param("field", in.fieldOfStudy()).param("start", Date.valueOf(in.startDate()))
                .param("end", in.endDate() == null ? null : Date.valueOf(in.endDate()))
                .param("grade", in.grade()).param("descr", in.description())
                .query(JdbcSeekerAssetsRepository::mapEducation).single();
    }

    @Override
    public Optional<EducationItem> updateEducation(UUID profileId, UUID id, EducationInput in) {
        return jdbc.sql("""
                UPDATE core.education SET institution = :inst, degree = :deg, field_of_study = :field,
                       start_date = :start, end_date = :end, grade = :grade, description = :descr
                 WHERE id = :id AND seeker_profile_id = :pid
                RETURNING """ + " " + EDU_COLUMNS)
                .param("id", id).param("pid", profileId).param("inst", in.institution()).param("deg", in.degree())
                .param("field", in.fieldOfStudy()).param("start", Date.valueOf(in.startDate()))
                .param("end", in.endDate() == null ? null : Date.valueOf(in.endDate()))
                .param("grade", in.grade()).param("descr", in.description())
                .query(JdbcSeekerAssetsRepository::mapEducation).optional();
    }

    @Override
    public boolean deleteEducation(UUID profileId, UUID id) {
        return jdbc.sql("DELETE FROM core.education WHERE id = :id AND seeker_profile_id = :pid")
                .param("id", id).param("pid", profileId).update() > 0;
    }

    // -------------------------------------------------------------- experience

    private static final String EXP_COLUMNS =
            "id, title, company_name, location, employment_type, start_date, end_date, is_current, description";

    private static ExperienceItem mapExperience(ResultSet rs, int n) throws SQLException {
        return new ExperienceItem(Db.uuid(rs, "id"), rs.getString("title"), rs.getString("company_name"),
                rs.getString("location"), rs.getString("employment_type"), rs.getObject("start_date", LocalDate.class),
                rs.getObject("end_date", LocalDate.class), rs.getBoolean("is_current"), rs.getString("description"));
    }

    @Override
    public List<ExperienceItem> experience(UUID profileId) {
        return jdbc.sql("SELECT " + EXP_COLUMNS + " FROM core.experience WHERE seeker_profile_id = :pid "
                + "ORDER BY is_current DESC, start_date DESC, created_at DESC").param("pid", profileId)
                .query(JdbcSeekerAssetsRepository::mapExperience).list();
    }

    @Override
    public ExperienceItem insertExperience(UUID profileId, UUID id, ExperienceInput in) {
        return jdbc.sql("""
                INSERT INTO core.experience (id, seeker_profile_id, title, company_name, location, employment_type,
                                             start_date, end_date, is_current, description)
                VALUES (:id, :pid, :title, :company, :loc, :etype, :start, :end, :cur, :descr)
                RETURNING """ + " " + EXP_COLUMNS)
                .param("id", id).param("pid", profileId).param("title", in.title()).param("company", in.companyName())
                .param("loc", in.location()).param("etype", in.employmentType())
                .param("start", Date.valueOf(in.startDate()))
                .param("end", in.endDate() == null ? null : Date.valueOf(in.endDate()))
                .param("cur", in.current()).param("descr", in.description())
                .query(JdbcSeekerAssetsRepository::mapExperience).single();
    }

    @Override
    public Optional<ExperienceItem> updateExperience(UUID profileId, UUID id, ExperienceInput in) {
        return jdbc.sql("""
                UPDATE core.experience SET title = :title, company_name = :company, location = :loc,
                       employment_type = :etype, start_date = :start, end_date = :end, is_current = :cur,
                       description = :descr
                 WHERE id = :id AND seeker_profile_id = :pid
                RETURNING """ + " " + EXP_COLUMNS)
                .param("id", id).param("pid", profileId).param("title", in.title()).param("company", in.companyName())
                .param("loc", in.location()).param("etype", in.employmentType())
                .param("start", Date.valueOf(in.startDate()))
                .param("end", in.endDate() == null ? null : Date.valueOf(in.endDate()))
                .param("cur", in.current()).param("descr", in.description())
                .query(JdbcSeekerAssetsRepository::mapExperience).optional();
    }

    @Override
    public boolean deleteExperience(UUID profileId, UUID id) {
        return jdbc.sql("DELETE FROM core.experience WHERE id = :id AND seeker_profile_id = :pid")
                .param("id", id).param("pid", profileId).update() > 0;
    }

    // ----------------------------------------------------------------- resumes

    private static ResumeItem mapResume(ResultSet rs, int n) throws SQLException {
        return new ResumeItem(Db.uuid(rs, "id"), rs.getString("original_filename"), rs.getString("content_type"),
                rs.getInt("size_bytes"), rs.getBoolean("is_primary"), Db.instant(rs, "created_at"));
    }

    @Override
    public List<ResumeItem> resumes(UUID profileId) {
        return jdbc.sql("""
                SELECT id, original_filename, content_type, size_bytes, is_primary, created_at
                  FROM core.resumes WHERE seeker_profile_id = :pid AND deleted_at IS NULL
                 ORDER BY is_primary DESC, created_at DESC
                """).param("pid", profileId).query(JdbcSeekerAssetsRepository::mapResume).list();
    }

    @Override
    public int activeResumeCount(UUID profileId) {
        return jdbc.sql("SELECT count(*) FROM core.resumes WHERE seeker_profile_id = :pid AND deleted_at IS NULL")
                .param("pid", profileId).query(Integer.class).single();
    }

    @Override
    public ResumeItem insertResume(UUID profileId, UUID id, String filename, String storageKey, String contentType,
            int size, String sha256, boolean primary, Instant now) {
        return jdbc.sql("""
                INSERT INTO core.resumes (id, seeker_profile_id, original_filename, storage_key, content_type,
                                          size_bytes, sha256, is_primary, created_at)
                VALUES (:id, :pid, :fn, :key, :ct, :size, :sha, :prim, :at)
                RETURNING id, original_filename, content_type, size_bytes, is_primary, created_at
                """).param("id", id).param("pid", profileId).param("fn", filename).param("key", storageKey)
                .param("ct", contentType).param("size", size).param("sha", sha256).param("prim", primary)
                .param("at", Db.ts(now)).query(JdbcSeekerAssetsRepository::mapResume).single();
    }

    @Override
    public Optional<ResumeFile> findResume(UUID profileId, UUID id) {
        return jdbc.sql("""
                SELECT id, original_filename, storage_key, content_type, size_bytes FROM core.resumes
                 WHERE id = :id AND seeker_profile_id = :pid AND deleted_at IS NULL
                """).param("id", id).param("pid", profileId)
                .query((rs, n) -> new ResumeFile(Db.uuid(rs, "id"), rs.getString("original_filename"),
                        rs.getString("storage_key"), rs.getString("content_type"), rs.getInt("size_bytes")))
                .optional();
    }

    @Override
    public boolean setPrimaryResume(UUID profileId, UUID id) {
        Boolean exists = jdbc.sql("SELECT EXISTS (SELECT 1 FROM core.resumes WHERE id = :id AND seeker_profile_id = :pid "
                + "AND deleted_at IS NULL)").param("id", id).param("pid", profileId).query(Boolean.class).single();
        if (!Boolean.TRUE.equals(exists)) {
            return false;
        }
        // The partial unique index allows a single primary: clear first, then set.
        jdbc.sql("UPDATE core.resumes SET is_primary = false WHERE seeker_profile_id = :pid AND is_primary AND id <> :id")
                .param("pid", profileId).param("id", id).update();
        jdbc.sql("UPDATE core.resumes SET is_primary = true WHERE id = :id").param("id", id).update();
        return true;
    }

    @Override
    public boolean softDeleteResume(UUID profileId, UUID id, Instant now) {
        Boolean wasPrimary = jdbc.sql("SELECT is_primary FROM core.resumes WHERE id = :id AND seeker_profile_id = :pid "
                + "AND deleted_at IS NULL").param("id", id).param("pid", profileId).query(Boolean.class).optional()
                .orElse(null);
        if (wasPrimary == null) {
            return false;
        }
        jdbc.sql("UPDATE core.resumes SET deleted_at = :at, is_primary = false WHERE id = :id")
                .param("at", Db.ts(now)).param("id", id).update();
        if (wasPrimary) {
            jdbc.sql("""
                    UPDATE core.resumes SET is_primary = true WHERE id = (
                      SELECT id FROM core.resumes WHERE seeker_profile_id = :pid AND deleted_at IS NULL
                       ORDER BY created_at DESC LIMIT 1)
                    """).param("pid", profileId).update();
        }
        return true;
    }
}
