package com.jobforge.aiservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Runtime tuning for the AI execution pipeline. */
@ConfigurationProperties(prefix = "jobforge.ai")
public record AiRuntimeProperties(
        @DefaultValue("local") String env,
        @DefaultValue("20") int providerTimeoutSeconds,
        @DefaultValue("1048576") int maxInputBytes,
        @DefaultValue Quota quota,
        @DefaultValue Retention retention) {
    public record Quota(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("true") boolean redisEnabled,
            @DefaultValue("120") int reservationTtlSeconds,
            @DefaultValue("60000") long sweepIntervalMs) {}
    public record Retention(
            @DefaultValue("90") int inputDays,
            @DefaultValue("1000") int batchSize) {}
}
