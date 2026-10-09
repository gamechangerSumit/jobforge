package com.jobforge.backend.auth.api;

import com.jobforge.backend.auth.app.AuthProperties;
import com.jobforge.backend.shared.config.ApiPaths;
import java.time.Duration;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/** {@code jf_refresh}: HttpOnly; SameSite=Strict; Path=/api/v1/auth; Max-Age=14d; Secure per COOKIE_SECURE (API_CONTRACT §3). */
@Component
public class RefreshCookieFactory {

    public static final String NAME = "jf_refresh";

    private final AuthProperties props;

    public RefreshCookieFactory(AuthProperties props) {
        this.props = props;
    }

    public String issue(String token, long maxAgeSeconds) {
        return build(token, Duration.ofSeconds(maxAgeSeconds)).toString();
    }

    public String clear() {
        return build("", Duration.ZERO).toString();
    }

    private ResponseCookie build(String value, Duration maxAge) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(props.cookieSecure())
                .sameSite("Strict")
                .path(ApiPaths.BASE + "/auth")
                .maxAge(maxAge);
        if (props.cookieDomain() != null && !props.cookieDomain().isBlank()) {
            builder.domain(props.cookieDomain());
        }
        return builder.build();
    }
}
