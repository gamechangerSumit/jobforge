package com.jobforge.backend.shared.security;

import com.jobforge.backend.shared.domain.UserRole;
import com.jobforge.backend.shared.domain.UserStatus;
import java.util.UUID;

public record VerifiedAccount(UUID id, UserRole role, UserStatus status) {}
