package com.jobforge.backend.profile.api;

import com.jobforge.backend.profile.domain.ApprovalStatus;
import com.jobforge.backend.profile.domain.RecruiterProfile;
import java.time.Instant;
import java.util.UUID;

public record RecruiterProfileResponse(
        UUID id, UUID userId, String jobTitle, String phone, ApprovalStatus approvalStatus, Instant updatedAt) {

    static RecruiterProfileResponse from(RecruiterProfile p) {
        return new RecruiterProfileResponse(p.id(), p.userId(), p.jobTitle(), p.phone(), p.approvalStatus(), p.updatedAt());
    }
}
