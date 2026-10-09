package com.jobforge.backend.platform.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code jobforge.rate-limit.*}: requests per minute per subject. Env: RATE_LIMIT_UPLOAD_PER_MINUTE. */
@ConfigurationProperties(prefix = "jobforge.rate-limit")
public record RateLimitProperties(Boolean enabled, Integer uploadPerMinute) {

    public RateLimitProperties {
        enabled = enabled == null || enabled;
        uploadPerMinute = uploadPerMinute == null || uploadPerMinute < 1 ? 10 : uploadPerMinute;
    }

    public int limitFor(RateLimitClass rateClass) {
        return switch (rateClass) {
            case UPLOAD -> uploadPerMinute;
            // API_CONTRACT section 9 (requests per minute per subject)
            case AUTH -> 10;
            case DEFAULT -> 120;
            case SEARCH -> 60;
            case WRITE_COMMUNITY -> 30;
            case AI -> 10;
        };
    }
}
