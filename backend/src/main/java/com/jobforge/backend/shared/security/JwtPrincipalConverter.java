package com.jobforge.backend.shared.security;

import com.jobforge.backend.shared.domain.UserStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/** Turns a signature-valid JWT into an {@link AuthenticatedUser}, re-checking the account server-side. */
public class JwtPrincipalConverter implements Converter<Jwt, UsernamePasswordAuthenticationToken> {

    private final AccessTokenVerifier verifier;

    public JwtPrincipalConverter(AccessTokenVerifier verifier) {
        this.verifier = verifier;
    }

    @Override
    public UsernamePasswordAuthenticationToken convert(Jwt jwt) {
        UUID userId;
        int version;
        try {
            userId = UUID.fromString(jwt.getSubject());
            Number ver = jwt.getClaim("ver");
            if (ver == null) {
                throw new BadCredentialsException("Missing token version");
            }
            version = ver.intValue();
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BadCredentialsException("Malformed token claims");
        }
        VerifiedAccount account = verifier.verify(userId, version)
                .orElseThrow(() -> new BadCredentialsException("Token no longer valid"));
        if (account.status() == UserStatus.SUSPENDED) {
            throw new AccountSuspendedAuthenticationException();
        }
        if (account.status() == UserStatus.DELETED) {
            throw new BadCredentialsException("Account deleted");
        }
        AuthenticatedUser principal = new AuthenticatedUser(account.id(), account.role());
        return UsernamePasswordAuthenticationToken.authenticated(
                principal, jwt.getTokenValue(), List.of(new SimpleGrantedAuthority("ROLE_" + account.role().name())));
    }
}
