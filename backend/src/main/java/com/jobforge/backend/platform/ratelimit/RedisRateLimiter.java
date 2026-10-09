package com.jobforge.backend.platform.ratelimit;

import com.jobforge.backend.shared.error.RateLimitedException;
import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Fixed one-minute window counter in Redis, key {@code jf:{env}:rl:{class}:{subject}:{epochMinute}} with a TTL
 * (ARCHITECTURE section 15 allows the window variant of the bucket). A Redis outage degrades to "no throttling"
 * and logs a warning: availability wins over throttling here, authorization is unaffected.
 */
@Component
@ConditionalOnProperty(name = "jobforge.rate-limit.enabled", havingValue = "true", matchIfMissing = true)
public class RedisRateLimiter implements RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimiter.class);
    private static final long WINDOW_SECONDS = 60;

    private final StringRedisTemplate redis;
    private final RateLimitProperties properties;
    private final Clock clock;
    private final String env;

    public RedisRateLimiter(StringRedisTemplate redis, RateLimitProperties properties, Clock clock,
            @Value("${jobforge.env:local}") String env) {
        this.redis = redis;
        this.properties = properties;
        this.clock = clock;
        this.env = env;
    }

    @Override
    public void check(RateLimitClass rateClass, String subject) {
        int limit = properties.limitFor(rateClass);
        long now = clock.instant().getEpochSecond();
        long window = now / WINDOW_SECONDS;
        String key = "jf:" + env + ":rl:" + rateClass.name().toLowerCase() + ":" + subject + ":" + window;
        Long count;
        try {
            count = redis.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redis.expire(key, Duration.ofSeconds(WINDOW_SECONDS * 2)); // TTL is mandatory
            }
        } catch (DataAccessException ex) {
            log.warn("Rate limiter unavailable, allowing request (class={})", rateClass);
            return;
        }
        if (count != null && count > limit) {
            long retryAfter = (window + 1) * WINDOW_SECONDS - now;
            throw new RateLimitedException("Too many requests. Please retry shortly.", retryAfter);
        }
    }
}
