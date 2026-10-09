package com.jobforge.backend.auth.app;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** {@code jobforge.auth.*} (ARCHITECTURE §27). TTL defaults: refresh 14 d (D-05), verification 24 h (§8). */
@ConfigurationProperties(prefix = "jobforge.auth")
public record AuthProperties(
        @DefaultValue("false") boolean cookieSecure,
        String cookieDomain,
        @DefaultValue("14") int refreshTtlDays,
        @DefaultValue("24") int verificationTtlHours,
        @DefaultValue("60") int passwordResetTtlMinutes,
        @DefaultValue("http://localhost:3000") String appBaseUrl,
        @DefaultValue("no-reply@jobforge.local") String mailFrom) {}
