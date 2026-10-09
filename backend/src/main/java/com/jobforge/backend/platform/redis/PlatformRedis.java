package com.jobforge.backend.platform.redis;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class PlatformRedis {

    private final StringRedisTemplate redis;

    public PlatformRedis(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void set(
            String key,
            String value,
            Duration ttl
    ) {
        redis.opsForValue().set(key, value, ttl);
    }

    public String get(String key) {
        return redis.opsForValue().get(key);
    }

    public Boolean delete(String key) {
        return redis.delete(key);
    }

    public Boolean increment(
            String key,
            Duration ttl
    ) {
        Long value = redis.opsForValue().increment(key);

        if (value != null && value == 1L) {
            redis.expire(key, ttl);
        }

        return value != null;
    }

    public void publish(
            String channel,
            String message
    ) {
        redis.convertAndSend(channel, message);
    }
}