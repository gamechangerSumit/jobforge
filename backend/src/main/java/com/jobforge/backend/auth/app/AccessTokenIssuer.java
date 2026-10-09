package com.jobforge.backend.auth.app;

import com.jobforge.backend.shared.security.SecurityProperties;
import com.jobforge.backend.user.facade.UserAccountView;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/** Issues the 15-minute HS256 access token with claims {@code sub, role, ver, iat, exp, jti} (API_CONTRACT §3). */
@Component
public class AccessTokenIssuer {

    private final JwtEncoder encoder;
    private final Clock clock;
    private final Duration ttl;

    public AccessTokenIssuer(JwtEncoder encoder, Clock clock, SecurityProperties properties) {
        this.encoder = encoder;
        this.clock = clock;
        this.ttl = Duration.ofMinutes(properties.jwt().accessTtlMinutes());
    }

    public String issue(UserAccountView user) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(user.id().toString())
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .id(UUID.randomUUID().toString())
                .claim("role", user.role().name())
                .claim("ver", user.tokenVersion())
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    public long expiresInSeconds() {
        return ttl.toSeconds();
    }
}
