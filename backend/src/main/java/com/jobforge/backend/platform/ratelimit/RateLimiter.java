package com.jobforge.backend.platform.ratelimit;

import com.jobforge.backend.shared.error.RateLimitedException;

/** Platform abstraction for per-subject request throttling (CLAUDE.md: Redis only through platform). */
public interface RateLimiter {

    /**
     * Counts one request for {@code subject} (a user id or an IP) in the given class.
     *
     * @throws RateLimitedException (429, with Retry-After) when the window budget is exhausted
     */
    void check(RateLimitClass rateClass, String subject);
}
