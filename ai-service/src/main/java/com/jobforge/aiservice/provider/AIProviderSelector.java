package com.jobforge.aiservice.provider;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AIProviderSelector {

    private final String provider;
    private final AIProvider localProvider;

    public AIProviderSelector(
            @Value("${jobforge.ai.provider:local}")
            String provider,
            AIProvider localProvider
    ) {
        this.provider = provider;
        this.localProvider = localProvider;
    }

    public AIProvider select() {

        if ("local".equalsIgnoreCase(provider)) {
            return localProvider;
        }

        throw new IllegalStateException(
                "Unsupported AI provider: " + provider
        );
    }
}