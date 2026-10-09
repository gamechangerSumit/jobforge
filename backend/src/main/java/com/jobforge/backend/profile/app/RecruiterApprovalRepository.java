package com.jobforge.backend.profile.app;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecruiterApprovalRepository {

    record Row(UUID userId, String email, String firstName, String lastName, String jobTitle, String approvalStatus,
            String rejectionReason, String companyName, Instant createdAt) {}

    /** Admin detail view: the list row plus contact phone and the linked company id. */
    record Detail(Row row, String phone, UUID companyId) {}

    Optional<Detail> find(UUID userId);

    List<Row> list(String status, int limit, int offset);

    long count(String status);

    /** Locks the profile row and returns its previous status, or empty when the user has no recruiter profile. */
    Optional<String> setStatus(UUID userId, String status, UUID adminId, String reason, Instant now);
}
