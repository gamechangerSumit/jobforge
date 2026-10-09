package com.jobforge.backend.auth.app;

import com.jobforge.backend.user.facade.UserAccountView;

/** Result types returned by {@link AuthService}; contain raw tokens, so never log them. */
public final class AuthResults {

    private AuthResults() {}

    public record Session(
            String accessToken, long expiresInSeconds, String refreshToken, long refreshMaxAgeSeconds, UserAccountView user) {
        @Override
        public String toString() {
            return "Session[user=" + user + "]";
        }
    }

    public record Me(UserAccountView user, boolean recruiterApproved, boolean companyVerified) {}
}
