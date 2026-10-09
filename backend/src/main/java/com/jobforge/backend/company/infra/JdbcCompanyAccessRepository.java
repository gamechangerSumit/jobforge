package com.jobforge.backend.company.infra;

import com.jobforge.backend.company.app.CompanyAccessRepository;
import com.jobforge.backend.company.facade.CompanyAccessFacade.CompanyInfo;
import com.jobforge.backend.company.facade.CompanyAccessFacade.Membership;
import com.jobforge.backend.shared.persistence.Db;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcCompanyAccessRepository implements CompanyAccessRepository {

    private final JdbcClient jdbc;

    public JdbcCompanyAccessRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Membership> findMembership(UUID userId) {
        return jdbc.sql("""
                SELECT cm.company_id, cm.member_role
                  FROM core.company_members cm JOIN core.companies c ON c.id = cm.company_id
                 WHERE cm.user_id = :uid AND c.deleted_at IS NULL
                """).param("uid", userId)
                .query((rs, n) -> new Membership(Db.uuid(rs, "company_id"), rs.getString("member_role")))
                .optional();
    }

    @Override
    public Map<UUID, CompanyInfo> findSummaries(Collection<UUID> companyIds) {
        Map<UUID, CompanyInfo> result = new HashMap<>();
        jdbc.sql("SELECT id, name, slug, verification_status FROM core.companies WHERE id IN (:ids)")
                .param("ids", companyIds)
                .query((rs, n) -> new CompanyInfo(Db.uuid(rs, "id"), rs.getString("name"), rs.getString("slug"), null,
                        "VERIFIED".equals(rs.getString("verification_status"))))
                .list().forEach(c -> result.put(c.id(), c));
        return result;
    }

    @Override
    public boolean isVerified(UUID companyId) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM core.companies WHERE id = :id AND verification_status = 'VERIFIED' AND deleted_at IS NULL)")
                .param("id", companyId).query(Boolean.class).single();
    }
}
