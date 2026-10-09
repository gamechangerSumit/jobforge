package com.jobforge.backend.platform.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RedisCacheServiceTest {

    private static final TypeReference<List<String>> LIST = new TypeReference<>() { };

    private StringRedisTemplate redis;
    private ValueOperations<String, String> ops;
    private RedisCacheService cache;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        cache = new RedisCacheService(redis, new ObjectMapper(), "test");
    }

    @Test
    void missLoadsAndStoresWithTtlUnderNamespacedKey() {
        when(ops.get("jf:test:cache:skills:q:ja:10")).thenReturn(null);
        List<String> value = cache.getOrLoad("skills:q:ja:10", Duration.ofSeconds(120), LIST, () -> List.of("java"));
        assertThat(value).containsExactly("java");
        verify(ops).set(eq("jf:test:cache:skills:q:ja:10"), eq("[\"java\"]"), eq(Duration.ofSeconds(120)));
    }

    @Test
    void hitSkipsTheLoader() {
        when(ops.get(anyString())).thenReturn("[\"cached\"]");
        AtomicInteger loads = new AtomicInteger();
        List<String> value = cache.getOrLoad("k:1", Duration.ofSeconds(5), LIST, () -> { loads.incrementAndGet(); return List.of("x"); });
        assertThat(value).containsExactly("cached");
        assertThat(loads).hasValue(0);
    }

    @Test
    void redisOutageFallsBackToTheLoader() {
        when(ops.get(anyString())).thenThrow(new RedisConnectionFailureException("down"));
        List<String> value = cache.getOrLoad("k:1", Duration.ofSeconds(5), LIST, () -> List.of("fresh"));
        assertThat(value).containsExactly("fresh");
    }

    @Test
    void corruptEntryFallsBackToTheLoader() {
        when(ops.get(anyString())).thenReturn("{not json");
        assertThat(cache.getOrLoad("k:1", Duration.ofSeconds(5), LIST, () -> List.of("fresh"))).containsExactly("fresh");
    }

    @Test
    void ttlIsMandatory() {
        assertThatThrownBy(() -> cache.getOrLoad("k:1", Duration.ZERO, LIST, List::of)).isInstanceOf(IllegalArgumentException.class);
        verify(ops, never()).set(anyString(), anyString(), org.mockito.ArgumentMatchers.any(Duration.class));
    }
}
