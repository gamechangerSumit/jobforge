package com.jobforge.backend.auth.api;

import com.jobforge.backend.auth.app.AuthResults;
import com.jobforge.backend.shared.domain.UserRole;
import com.jobforge.backend.user.facade.UserAccountView;
import java.util.UUID;

/** Response DTOs (API_CONTRACT §12.1). Never contain password hashes or refresh tokens. */
public final class AuthResponses {

    private AuthResponses() {}

    public record AuthUser(UUID id, String email, UserRole role, String firstName, String lastName, String handle, boolean emailVerified) {
        static AuthUser from(UserAccountView u) {
            return new AuthUser(u.id(), u.email(), u.role(), u.firstName(), u.lastName(), u.handle(), u.emailVerified());
        }
    }

    public record Registered(AuthUser user) {
        static Registered from(UserAccountView u) {
            return new Registered(AuthUser.from(u));
        }
    }

    public record Login(String accessToken, String tokenType, long expiresIn, AuthUser user) {
        static Login from(AuthResults.Session s) {
            return new Login(s.accessToken(), "Bearer", s.expiresInSeconds(), AuthUser.from(s.user()));
        }
    }

    public record Me(UUID id, String email, UserRole role, String firstName, String lastName, String handle,
            boolean emailVerified, boolean recruiterApproved, boolean companyVerified) {
        static Me from(AuthResults.Me m) {
            UserAccountView u = m.user();
            return new Me(u.id(), u.email(), u.role(), u.firstName(), u.lastName(), u.handle(), u.emailVerified(),
                    m.recruiterApproved(), m.companyVerified());
        }
    }
}
