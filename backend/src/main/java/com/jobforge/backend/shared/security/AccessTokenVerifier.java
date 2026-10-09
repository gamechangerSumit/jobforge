package com.jobforge.backend.shared.security;

import java.util.Optional;
import java.util.UUID;

/**
 * Server-side re-check of a signed access token (ARCHITECTURE §8.4): the user must exist and
 * {@code ver} must equal {@code users.token_version}. Implemented by the {@code auth} module.
 */
public interface AccessTokenVerifier {

    /** @return empty when the user is unknown/deleted or the token version is stale. */
    Optional<VerifiedAccount> verify(UUID userId, int tokenVersion);
}
