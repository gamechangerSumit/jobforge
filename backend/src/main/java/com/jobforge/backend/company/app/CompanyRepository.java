package com.jobforge.backend.company.app;

import com.jobforge.backend.company.app.CompanyModels.AdminCompanyRow;
import com.jobforge.backend.company.app.CompanyModels.CompanyDraft;
import com.jobforge.backend.company.app.CompanyModels.CompanyView;
import com.jobforge.backend.company.app.CompanyModels.MemberView;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence port of the company module. All SQL is parameterised; soft-deleted rows are never returned. */
public interface CompanyRepository {

    boolean slugExists(String slug);

    void insert(UUID id, String slug, CompanyDraft draft, UUID createdBy);

    void insertMember(UUID companyId, UUID userId, String memberRole);

    Optional<CompanyView> findById(UUID id);

    /** Returns the new version or empty when {@code expectedVersion} is stale. */
    Optional<Long> update(UUID id, CompanyDraft draft, long expectedVersion);

    /** Verified companies only, optional name filter, offset paging. */
    List<CompanyView> listVerified(String q, int limit, int offset);

    long countVerified(String q);

    List<MemberView> members(UUID companyId);

    Optional<com.jobforge.backend.company.facade.CompanyAccessFacade.Membership> findMembershipCompany(UUID userId);

    Optional<UUID> findUserIdByEmail(String email);

    Optional<String> userRole(UUID userId);

    Optional<String> approvalStatus(UUID userId);

    boolean removeMember(UUID companyId, UUID userId);

    long ownerCount(UUID companyId);

    // admin
    List<AdminCompanyRow> adminList(String status, int limit, int offset);

    long adminCount(String status);

    /** Sets the moderation outcome; returns the previous status or empty when the company does not exist. */
    Optional<String> setVerification(UUID id, String status, UUID adminId, String reason, Instant now);

    Optional<UUID> ownerOf(UUID companyId);

    Optional<String> findLogoKey(UUID companyId);

    void setLogoKey(UUID companyId, String key);
}
