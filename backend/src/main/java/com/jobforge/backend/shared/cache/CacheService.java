package com.jobforge.backend.shared.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import java.time.Duration;
import java.util.function.Supplier;

/**
 * Read-through cache port (CLAUDE.md: Redis only through platform abstractions). A TTL is mandatory. Implementations
 * must degrade gracefully: if the cache is unavailable the loader result is returned uncached. Never cache data that
 * depends on the caller's identity unless the key includes it.
 */
public interface CacheService {

    /** Returns the cached value for {@code key} or loads, stores (for {@code ttl}) and returns it. */
    <T> T getOrLoad(String key, Duration ttl, TypeReference<T> type, Supplier<T> loader);
}
