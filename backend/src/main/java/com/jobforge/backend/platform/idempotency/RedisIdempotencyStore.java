package com.jobforge.backend.platform.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobforge.backend.shared.idempotency.IdempotencyStore;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis binding of the idempotency port (ARCHITECTURE 13, key pattern {@code jf:{env}:idem:{userId}:{key}}, 24 h TTL).
 * Survives restarts and works across instances. Redis is a performance/UX layer only: database unique constraints
 * stay the source of truth, so a Redis outage degrades to "no replay" instead of failing the request.
 */
@Component
@ConditionalOnProperty(name = "jobforge.idempotency.store", havingValue = "redis", matchIfMissing = true)
public class RedisIdempotencyStore implements IdempotencyStore {

    private static final Logger log = LoggerFactory.getLogger(RedisIdempotencyStore.class);
    private static final Duration TTL = Duration.ofHours(24);
    private static final String ALLOWED_TYPE_PREFIX = "com.jobforge.backend.";

    private record Entry(String hash, boolean done, String type, String body) {}

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final String env;

    public RedisIdempotencyStore(StringRedisTemplate redis, ObjectMapper mapper,
            @Value("${jobforge.env:local}") String env) {
        this.redis = redis;
        this.mapper = mapper;
        this.env = env;
    }

    private String key(String scope) {
        return "jf:" + env + ":idem:" + scope;
    }

    @Override
    public Outcome begin(String scope, String requestHash) {
        try {
            String key = key(scope);
            for (int attempt = 0; attempt < 2; attempt++) {
                Boolean created = redis.opsForValue().setIfAbsent(key, write(new Entry(requestHash, false, null, null)), TTL);
                if (Boolean.TRUE.equals(created)) {
                    return new Outcome(Kind.NEW, null);
                }
                String raw = redis.opsForValue().get(key);
                if (raw == null) {
                    continue; // expired between the two calls: try to claim again
                }
                Entry existing = mapper.readValue(raw, Entry.class);
                if (!existing.hash().equals(requestHash)) {
                    return new Outcome(Kind.MISMATCH, null);
                }
                return existing.done() ? new Outcome(Kind.REPLAY, read(existing)) : new Outcome(Kind.IN_PROGRESS, null);
            }
            return new Outcome(Kind.NEW, null);
        } catch (DataAccessException | java.io.IOException | ClassNotFoundException e) {
            log.warn("Idempotency store unavailable, continuing without replay protection: {}", e.getClass().getSimpleName());
            return new Outcome(Kind.NEW, null);
        }
    }

    @Override
    public void complete(String scope, Object response) {
        try {
            String raw = redis.opsForValue().get(key(scope));
            if (raw == null) {
                return;
            }
            Entry current = mapper.readValue(raw, Entry.class);
            Entry done = new Entry(current.hash(), true, response.getClass().getName(), mapper.writeValueAsString(response));
            redis.opsForValue().set(key(scope), write(done), TTL);
        } catch (DataAccessException | java.io.IOException e) {
            log.warn("Could not record idempotent response: {}", e.getClass().getSimpleName());
        }
    }

    @Override
    public void abort(String scope) {
        try {
            String raw = redis.opsForValue().get(key(scope));
            if (raw != null && !mapper.readValue(raw, Entry.class).done()) {
                redis.delete(key(scope));
            }
        } catch (DataAccessException | java.io.IOException e) {
            log.warn("Could not release idempotency key: {}", e.getClass().getSimpleName());
        }
    }

    private String write(Entry entry) throws java.io.IOException {
        return mapper.writeValueAsString(entry);
    }

    private Object read(Entry entry) throws ClassNotFoundException, java.io.IOException {
        if (entry.type() == null || !entry.type().startsWith(ALLOWED_TYPE_PREFIX)) {
            throw new ClassNotFoundException("Refusing to deserialize " + entry.type());
        }
        return mapper.readValue(entry.body(), Class.forName(entry.type()));
    }
}
