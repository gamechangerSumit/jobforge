package com.jobforge.aiservice.provider;

public interface AIProvider {

    ProviderInfo info();

    ProviderResult generate(
            ProviderRequest request
    );

    record ProviderInfo(
            String provider,
            String model
    ) {
    }

    record ProviderRequest(
            String operation,
            String input,
            String traceId
    ) {
    }

    record ProviderResult(
            String output,
            int inputTokens,
            int outputTokens
    ) {
    }
}