package com.jobforge.backend.platform.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.jobforge.backend.shared.cache.CacheService;
import java.time.Duration;
import java.util.function.Supplier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Used when {@code jobforge.cache.enabled=false} (integration tests have no Redis). Always loads. */
@Component
@ConditionalOnProperty(name = "jobforge.cache.enabled", havingValue = "false")
public class NoopCacheService implements CacheService {

    @Override
    public <T> T getOrLoad(String key, Duration ttl, TypeReference<T> type, Supplier<T> loader) {
        return loader.get();
    }
}
