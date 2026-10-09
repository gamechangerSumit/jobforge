package com.jobforge.backend.company.infra;

import com.jobforge.backend.company.app.CompanyModels.AdminCompanyRow;
import com.jobforge.backend.company.app.CompanyModels.CompanyDraft;
import com.jobforge.backend.company.app.CompanyModels.CompanyView;
import com.jobforge.backend.company.app.CompanyModels.MemberView;
import com.jobforge.backend.company.app.CompanyRepository;
import com.jobforge.backend.shared.persistence.Db;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcCompanyRepository implements CompanyRepository {

    private static final String VIEW_SELECT = """
            SELECT c.id, c.name, c.slug, c.description, c.industry, c.size_band, c.website_url, c.hq_city, c.hq_state,
                   c.hq_country::text AS hq_country, c.logo_key, c.founded_year, c.verification_status, c.rejection_reason,
                   c.version, c.created_at,
                   (SELECT count(*) FROM core.jobs j
                     WHERE j.company_id = c.id AND j.status = 'PUBLISHED' AND j.deleted_at IS NULL) AS open_jobs
              FROM core.companies c
            """;

    private final JdbcClient jdbc;

    public JdbcCompanyRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private static String logoUrl(ResultSet rs) throws SQLException {
        String key = rs.getString("logo_key");
        return key == null ? null : com.jobforge.backend.shared.config.ApiPaths.BASE + "/companies/"
                + Db.uuid(rs, "id") + "/logo?v=" + Integer.toHexString(key.hashCode());
    }

    private static CompanyView mapView(ResultSet rs, int n) throws SQLException {
        String status = rs.getString("verification_status");
        int founded = rs.getInt("founded_year");
        Integer foundedYear = rs.wasNull() ? null : founded;
        return new CompanyView(Db.uuid(rs, "id"), rs.getString("name"), rs.getString("slug"),
                rs.getString("description"), rs.getString("industry"), rs.getString("size_band"),
                rs.getString("website_url"), logoUrl(rs), rs.getString("hq_city"), rs.getString("hq_state"),
                rs.getString("hq_country"), foundedYear, status, "VERIFIED".equals(status),
                rs.getString("rejection_reason"), rs.getLong("open_jobs"), rs.getLong("version"),
                Db.instant(rs, "created_at"));
    }

    @Override
    public boolean slugExists(String slug) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM core.companies WHERE slug = :s)").param("s", slug)
                .query(Boolean.class).single();
    }

    @Override
    public void insert(UUID id, String slug, CompanyDraft d, UUID createdBy) {
        jdbc.sql("""
                INSERT INTO core.companies (id, name, slug, description, industry, size_band, website_url, hq_city,
                                            hq_state, hq_country, founded_year, created_by)
                VALUES (:id, :name, :slug, :descr, :industry, :size, :web, :city, :state, :country, :founded, :by)
                """)
                .param("id", id).param("name", d.name()).param("slug", slug).param("descr", d.description())
                .param("industry", d.industry()).param("size", d.sizeBand()).param("web", d.websiteUrl())
                .param("city", d.hqCity()).param("state", d.hqState()).param("country", d.hqCountry())
                .param("founded", d.foundedYear()).param("by", createdBy).update();
    }

    @Override
    public void insertMember(UUID companyId, UUID userId, String memberRole) {
        jdbc.sql("INSERT INTO core.company_members (company_id, user_id, member_role) VALUES (:c, :u, :r)")
                .param("c", companyId).param("u", userId).param("r", memberRole).update();
    }

    @Override
    public Optional<CompanyView> findById(UUID id) {
        return jdbc.sql(VIEW_SELECT + " WHERE c.id = :id AND c.deleted_at IS NULL").param("id", id)
                .query(JdbcCompanyRepository::mapView).optional();
    }

    @Override
    public Optional<Long> update(UUID id, CompanyDraft d, long expectedVersion) {
        return jdbc.sql("""
                UPDATE core.companies SET name = :name, description = :descr, industry = :industry,
                       size_band = :size, website_url = :web, hq_city = :city, hq_state = :state,
                       hq_country = :country, founded_year = :founded, version = version + 1, updated_at = now()
                 WHERE id = :id AND version = :v AND deleted_at IS NULL
                RETURNING version
                """)
                .param("id", id).param("v", expectedVersion).param("name", d.name()).param("descr", d.description())
                .param("industry", d.industry()).param("size", d.sizeBand()).param("web", d.websiteUrl())
                .param("city", d.hqCity()).param("state", d.hqState()).param("country", d.hqCountry())
                .param("founded", d.foundedYear()).query(Long.class).optional();
    }

    private static String escapeLike(String q) {
        return q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    @Override
    public List<CompanyView> listVerified(String q, int limit, int offset) {
        boolean filter = q != null && !q.isBlank();
        String sql = VIEW_SELECT + " WHERE c.verification_status = 'VERIFIED' AND c.deleted_at IS NULL"
                + (filter ? " AND c.name ILIKE :q ESCAPE '\\'" : "") + " ORDER BY c.name LIMIT :limit OFFSET :offset";
        JdbcClient.StatementSpec spec = jdbc.sql(sql).param("limit", limit).param("offset", offset);
        if (filter) {
            spec = spec.param("q", "%" + escapeLike(q.trim()) + "%");
        }
        return spec.query(JdbcCompanyRepository::mapView).list();
    }

    @Override
    public long countVerified(String q) {
        boolean filter = q != null && !q.isBlank();
        String sql = "SELECT count(*) FROM core.companies c WHERE c.verification_status = 'VERIFIED' "
                + "AND c.deleted_at IS NULL" + (filter ? " AND c.name ILIKE :q ESCAPE '\\'" : "");
        JdbcClient.StatementSpec spec = jdbc.sql(sql);
        if (filter) {
            spec = spec.param("q", "%" + escapeLike(q.trim()) + "%");
        }
        return spec.query(Long.class).single();
    }

    @Override
    public List<MemberView> members(UUID companyId) {
        return jdbc.sql("""
                SELECT u.id, u.first_name, u.last_name, u.email::text AS email, cm.member_role, cm.created_at
                  FROM core.company_members cm JOIN core.users u ON u.id = cm.user_id
                 WHERE cm.company_id = :c ORDER BY (cm.member_role = 'OWNER') DESC, cm.created_at
                """).param("c", companyId)
                .query((rs, n) -> new MemberView(Db.uuid(rs, "id"), rs.getString("first_name"),
                        rs.getString("last_name"), rs.getString("email"), rs.getString("member_role"),
                        Db.instant(rs, "created_at")))
                .list();
    }

    @Override
    public Optional<com.jobforge.backend.company.facade.CompanyAccessFacade.Membership> findMembershipCompany(UUID userId) {
        return jdbc.sql("""
                SELECT cm.company_id, cm.member_role
                  FROM core.company_members cm JOIN core.companies c ON c.id = cm.company_id
                 WHERE cm.user_id = :uid AND c.deleted_at IS NULL
                """).param("uid", userId)
                .query((rs, n) -> new com.jobforge.backend.company.facade.CompanyAccessFacade.Membership(
                        Db.uuid(rs, "company_id"), rs.getString("member_role")))
                .optional();
    }

    @Override
    public Optional<UUID> findUserIdByEmail(String email) {
        return jdbc.sql("SELECT id FROM core.users WHERE email = :e AND deleted_at IS NULL").param("e", email)
                .query(UUID.class).optional();
    }

    @Override
    public Optional<String> userRole(UUID userId) {
        return jdbc.sql("SELECT role FROM core.users WHERE id = :id AND deleted_at IS NULL").param("id", userId)
                .query(String.class).optional();
    }

    @Override
    public Optional<String> approvalStatus(UUID userId) {
        return jdbc.sql("SELECT approval_status FROM core.recruiter_profiles WHERE user_id = :id")
                .param("id", userId).query(String.class).optional();
    }

    @Override
    public boolean removeMember(UUID companyId, UUID userId) {
        return jdbc.sql("DELETE FROM core.company_members WHERE company_id = :c AND user_id = :u")
                .param("c", companyId).param("u", userId).update() > 0;
    }

    @Override
    public long ownerCount(UUID companyId) {
        return jdbc.sql("SELECT count(*) FROM core.company_members WHERE company_id = :c AND member_role = 'OWNER'")
                .param("c", companyId).query(Long.class).single();
    }

    @Override
    public List<AdminCompanyRow> adminList(String status, int limit, int offset) {
        boolean filter = status != null;
        String sql = """
                SELECT c.id, c.name, c.slug, c.verification_status, c.rejection_reason, c.created_at,
                       (SELECT u.email::text FROM core.company_members m JOIN core.users u ON u.id = m.user_id
                         WHERE m.company_id = c.id AND m.member_role = 'OWNER' LIMIT 1) AS owner_email,
                       (SELECT count(*) FROM core.company_members m WHERE m.company_id = c.id) AS member_count
                  FROM core.companies c WHERE c.deleted_at IS NULL
                """ + (filter ? " AND c.verification_status = :status" : "")
                + " ORDER BY c.created_at DESC LIMIT :limit OFFSET :offset";
        JdbcClient.StatementSpec spec = jdbc.sql(sql).param("limit", limit).param("offset", offset);
        if (filter) {
            spec = spec.param("status", status);
        }
        return spec.query((rs, n) -> new AdminCompanyRow(Db.uuid(rs, "id"), rs.getString("name"),
                rs.getString("slug"), rs.getString("verification_status"), rs.getString("rejection_reason"),
                Db.instant(rs, "created_at"), rs.getString("owner_email"), rs.getLong("member_count"))).list();
    }

    @Override
    public long adminCount(String status) {
        boolean filter = status != null;
        JdbcClient.StatementSpec spec = jdbc.sql("SELECT count(*) FROM core.companies WHERE deleted_at IS NULL"
                + (filter ? " AND verification_status = :status" : ""));
        if (filter) {
            spec = spec.param("status", status);
        }
        return spec.query(Long.class).single();
    }

    @Override
    public Optional<String> setVerification(UUID id, String status, UUID adminId, String reason, Instant now) {
        Optional<String> previous = jdbc.sql("SELECT verification_status FROM core.companies WHERE id = :id "
                + "AND deleted_at IS NULL FOR UPDATE").param("id", id).query(String.class).optional();
        if (previous.isEmpty()) {
            return previous;
        }
        boolean verified = "VERIFIED".equals(status);
        jdbc.sql("""
                UPDATE core.companies SET verification_status = :status,
                       verified_by = :by, verified_at = :at, rejection_reason = :reason, version = version + 1, updated_at = now()
                 WHERE id = :id
                """).param("id", id).param("status", status).param("by", verified ? adminId : null)
                .param("at", verified ? Db.ts(now) : null).param("reason", verified ? null : reason).update();
        return previous;
    }

    @Override
    public Optional<UUID> ownerOf(UUID companyId) {
        return jdbc.sql("SELECT user_id FROM core.company_members WHERE company_id = :c AND member_role = 'OWNER' "
                + "ORDER BY created_at LIMIT 1").param("c", companyId).query(UUID.class).optional();
    }

    @Override
    public Optional<String> findLogoKey(UUID companyId) {
        java.util.List<String> keys = jdbc.sql("SELECT logo_key FROM core.companies WHERE id = :id AND deleted_at IS NULL")
                .param("id", companyId).query(String.class).list();
        return keys.isEmpty() || keys.get(0) == null ? Optional.empty() : Optional.of(keys.get(0));
    }

    @Override
    public void setLogoKey(UUID companyId, String key) {
        jdbc.sql("UPDATE core.companies SET logo_key = :key, updated_at = now() WHERE id = :id AND deleted_at IS NULL")
                .param("key", key).param("id", companyId).update();
    }
}
