package com.jobforge.backend.platform.events;

import com.jobforge.backend.shared.domain.UserRole;

import java.util.UUID;

public record EventActor(
        UUID userId,
        UserRole role
) {
}