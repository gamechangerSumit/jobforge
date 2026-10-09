package com.jobforge.backend.shared.security;

import com.jobforge.backend.shared.domain.UserRole;
import java.util.UUID;

/** Authentication principal derived from a verified access token (never contains secrets). */
public record AuthenticatedUser(UUID id, UserRole role) {}
