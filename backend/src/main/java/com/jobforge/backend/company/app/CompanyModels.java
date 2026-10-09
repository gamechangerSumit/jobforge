package com.jobforge.backend.company.app;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Read/command models of the company module (API_CONTRACT 12.3). */
public final class CompanyModels {

    private CompanyModels() {}

    public record CompanyView(UUID id, String name, String slug, String description, String industry, String sizeBand,
            String websiteUrl, String logoUrl, String hqCity, String hqState, String hqCountry, Integer foundedYear,
            String verificationStatus, boolean verified, String rejectionReason, long openJobCount, long version,
            Instant createdAt) {

        /** Public representation: moderation reason is only for members/admins. */
        public CompanyView publicView() {
            return new CompanyView(id, name, slug, description, industry, sizeBand, websiteUrl, logoUrl, hqCity, hqState,
                    hqCountry, foundedYear, verificationStatus, verified, null, openJobCount, version, createdAt);
        }
    }

    public record MemberView(UUID userId, String firstName, String lastName, String email, String memberRole,
            Instant joinedAt) {}

    public record MyCompanyView(CompanyView company, String memberRole, List<MemberView> members) {}

    public record CompanyDraft(String name, String description, String industry, String sizeBand, String websiteUrl,
            String hqCity, String hqState, String hqCountry, Integer foundedYear) {}

    public record AdminCompanyRow(UUID id, String name, String slug, String verificationStatus, String rejectionReason,
            Instant createdAt, String ownerEmail, long memberCount) {}
}
