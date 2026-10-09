package com.jobforge.backend.company.events;

import java.util.UUID;

/** ARCHITECTURE 14 payloads (topic jobforge.users.v1). Never carries the rejection reason text. */
public final class CompanyEvents {

    private CompanyEvents() {}

    public record CompanyVerified(UUID companyId, UUID ownerId, UUID verifiedBy) {}

    public record CompanyRejected(UUID companyId, UUID ownerId, UUID rejectedBy) {}
}
