package com.jobforge.backend.platform.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jobforge.backend.shared.error.RateLimitedException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RedisRateLimiterTest {

    // 2026-10-08T10:00:20Z -> 40 s left in the current minute window
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-08T10:00:20Z"), ZoneOffset.UTC);

    private StringRedisTemplate redis;
    private ValueOperations<String, String> ops;
    private RedisRateLimiter limiter;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        limiter = new RedisRateLimiter(redis, new RateLimitProperties(true, 3), CLOCK, "test");
    }

    @Test
    void allowsRequestsUpToTheLimitAndSetsTtlOnFirstHit() {
        when(ops.increment(anyString())).thenReturn(1L, 2L, 3L);
        limiter.check(RateLimitClass.UPLOAD, "u:1");
        limiter.check(RateLimitClass.UPLOAD, "u:1");
        limiter.check(RateLimitClass.UPLOAD, "u:1");

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(redis).expire(key.capture(), eq(Duration.ofSeconds(120))); // only the first hit sets the TTL
        assertThat(key.getValue()).startsWith("jf:test:rl:upload:u:1:");
    }

    @Test
    void rejectsTheRequestOverTheLimitWithRetryAfter() {
        when(ops.increment(anyString())).thenReturn(4L);
        assertThatThrownBy(() -> limiter.check(RateLimitClass.UPLOAD, "u:1"))
                .isInstanceOfSatisfying(RateLimitedException.class, ex -> assertThat(ex.retryAfterSeconds()).isEqualTo(40));
    }

    @Test
    void separateSubjectsUseSeparateKeys() {
        when(ops.increment(anyString())).thenReturn(1L);
        limiter.check(RateLimitClass.UPLOAD, "u:1");
        limiter.check(RateLimitClass.UPLOAD, "u:2");
        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(ops, org.mockito.Mockito.times(2)).increment(keys.capture());
        assertThat(keys.getAllValues()).doesNotHaveDuplicates();
    }

    @Test
    void failsOpenWhenRedisIsDown() {
        when(ops.increment(anyString())).thenThrow(new RedisConnectionFailureException("down"));
        assertThatCode(() -> limiter.check(RateLimitClass.UPLOAD, "u:1")).doesNotThrowAnyException();
        verify(redis, never()).expire(anyString(), org.mockito.ArgumentMatchers.any(Duration.class));
    }

    @Test
    void contractLimitsApplyToEveryClass() {
        when(ops.increment(anyString())).thenReturn(120L);
        assertThatCode(() -> limiter.check(RateLimitClass.DEFAULT, "u:1")).doesNotThrowAnyException();
        when(ops.increment(anyString())).thenReturn(121L);
        assertThatThrownBy(() -> limiter.check(RateLimitClass.DEFAULT, "u:1")).isInstanceOf(RateLimitedException.class);
        when(ops.increment(anyString())).thenReturn(11L);
        assertThatThrownBy(() -> limiter.check(RateLimitClass.AUTH, "ip:1")).isInstanceOf(RateLimitedException.class);
        when(ops.increment(anyString())).thenReturn(61L);
        assertThatThrownBy(() -> limiter.check(RateLimitClass.SEARCH, "ip:1")).isInstanceOf(RateLimitedException.class);
    }

    @Test
    void propertiesDefaultToTenPerMinute() {
        assertThat(new RateLimitProperties(null, null).limitFor(RateLimitClass.UPLOAD)).isEqualTo(10);
        assertThat(new RateLimitProperties(null, 0).limitFor(RateLimitClass.UPLOAD)).isEqualTo(10);
        assertThat(new RateLimitProperties(null, null).enabled()).isTrue();
    }
}
