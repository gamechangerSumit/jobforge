package com.jobforge.backend.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobforge.backend.shared.domain.UserRole;
import com.jobforge.backend.shared.domain.UserStatus;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;

class JwtPrincipalConverterTest {

    private final UUID id = UUID.randomUUID();

    private Jwt jwt(Object ver) {
        Jwt.Builder b = Jwt.withTokenValue("t").header("alg", "HS256").subject(id.toString());
        if (ver != null) {
            b.claim("ver", ver);
        } else {
            b.claim("x", "y");
        }
        return b.build();
    }

    private JwtPrincipalConverter converter(UserStatus status, int currentVersion) {
        return new JwtPrincipalConverter((userId, ver) ->
                ver == currentVersion ? Optional.of(new VerifiedAccount(userId, UserRole.RECRUITER, status)) : Optional.empty());
    }

    @Test
    void validTokenYieldsPrincipalAndRoleAuthority() {
        var auth = converter(UserStatus.ACTIVE, 3).convert(jwt(3));
        assertThat(auth.getPrincipal()).isEqualTo(new AuthenticatedUser(id, UserRole.RECRUITER));
        assertThat(auth.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_RECRUITER");
    }

    @Test
    void staleTokenVersionIsRejected() {
        assertThatThrownBy(() -> converter(UserStatus.ACTIVE, 4).convert(jwt(3))).isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void missingVersionClaimIsRejected() {
        assertThatThrownBy(() -> converter(UserStatus.ACTIVE, 0).convert(jwt(null))).isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void suspendedAccountIsReportedAsSuspended() {
        assertThatThrownBy(() -> converter(UserStatus.SUSPENDED, 0).convert(jwt(0)))
                .isInstanceOf(AccountSuspendedAuthenticationException.class);
    }

    @Test
    void deletedAccountIsRejected() {
        assertThatThrownBy(() -> converter(UserStatus.DELETED, 0).convert(jwt(0))).isInstanceOf(BadCredentialsException.class);
    }
}
