package com.jobforge.backend.platform.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobforge.backend.shared.cache.CacheService;
import java.time.Duration;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** JSON-in-Redis read-through cache, key {@code jf:{env}:cache:{key}}. Any Redis or (de)serialization problem falls back to the loader. */
@Component
@ConditionalOnProperty(name = "jobforge.cache.enabled", havingValue = "true", matchIfMissing = true)
public class RedisCacheService implements CacheService {

    private static final Logger log = LoggerFactory.getLogger(RedisCacheService.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final String env;

    public RedisCacheService(StringRedisTemplate redis, ObjectMapper mapper, @Value("${jobforge.env:local}") String env) {
        this.redis = redis;
        this.mapper = mapper;
        this.env = env;
    }

    @Override
    public <T> T getOrLoad(String key, Duration ttl, TypeReference<T> type, Supplier<T> loader) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("A positive TTL is mandatory");
        }
        String redisKey = "jf:" + env + ":cache:" + key;
        try {
            String cached = redis.opsForValue().get(redisKey);
            if (cached != null) {
                return mapper.readValue(cached, type);
            }
        } catch (RuntimeException | JsonProcessingException ex) {
            log.warn("Cache read failed, loading directly (key class={})", key.substring(0, Math.max(0, key.indexOf(':'))));
        }
        T value = loader.get();
        try {
            redis.opsForValue().set(redisKey, mapper.writeValueAsString(value), ttl);
        } catch (RuntimeException | JsonProcessingException ex) {
            log.warn("Cache write failed (key class={})", key.substring(0, Math.max(0, key.indexOf(':'))));
        }
        return value;
    }
}
