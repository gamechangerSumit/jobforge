package com.jobforge.backend.profile.api;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** PUT /recruiters/me body. {@code approvalStatus} is read-only and therefore not accepted. */
public record RecruiterProfileRequest(
        @Size(max = 100) String jobTitle,
        @Pattern(regexp = "^\\+[1-9]\\d{1,14}$") String phone) {}
