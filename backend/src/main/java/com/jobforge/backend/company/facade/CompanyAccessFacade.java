package com.jobforge.backend.company.facade;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Narrow company API needed by jobs/applications (membership, verification, summaries). If a broader
 * {@code CompanyFacade} already exists from Phase 1, merge these methods into it.
 */
public interface CompanyAccessFacade {

    record Membership(UUID companyId, String memberRole) {}

    /** CompanySummary (API_CONTRACT §11). {@code logoUrl} stays null until the storage module resolves logo keys. */
    record CompanyInfo(UUID id, String name, String slug, String logoUrl, boolean verified) {}

    /** One company per recruiter (v1). Soft-deleted companies are ignored. */
    Optional<Membership> membershipOf(UUID userId);

    Map<UUID, CompanyInfo> summaries(Collection<UUID> companyIds);

    /** {@code companies.verification_status = VERIFIED} and not deleted (D-16). */
    boolean isVerified(UUID companyId);

    /**
     * Account-deletion policy (REQ-20261009): a company OWNER cannot delete the account while the company still has
     * other members or open jobs. Returns the user-facing reason, or empty when deletion may proceed.
     */
    Optional<String> accountDeletionBlocker(UUID userId);

    /** Removes the user's company membership as part of account deletion (no-op when there is none). */
    void releaseMembership(UUID userId);
}
