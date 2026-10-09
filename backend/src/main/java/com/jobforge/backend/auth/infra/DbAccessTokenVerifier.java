package com.jobforge.backend.auth.infra;

import com.jobforge.backend.shared.security.AccessTokenVerifier;
import com.jobforge.backend.shared.security.VerifiedAccount;
import com.jobforge.backend.user.facade.UserFacade;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Compares the token's {@code ver} with {@code users.token_version} on every request (ARCHITECTURE §8.4).
 * A Redis cache (TTL 60 s) is deferred until the platform {@code CacheService} exists.
 */
@Component
public class DbAccessTokenVerifier implements AccessTokenVerifier {

    private final UserFacade users;

    public DbAccessTokenVerifier(UserFacade users) {
        this.users = users;
    }

    @Override
    public Optional<VerifiedAccount> verify(UUID userId, int tokenVersion) {
        return users.findById(userId)
                .filter(u -> u.tokenVersion() == tokenVersion)
                .map(u -> new VerifiedAccount(u.id(), u.role(), u.status()));
    }
}
