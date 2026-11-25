package com.jobforge.backend.ai.facade;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AiContextTextSanitizerTest {

    @Test
    void redactsEmailPhoneAndUrlFromFreeText() {
        String safe = AiContextTextSanitizer.sanitize(
                "Senior engineer; contact me at dev@example.com or +91 98765 43210; portfolio https://example.com/me");
        assertFalse(safe.contains("dev@example.com"));
        assertFalse(safe.contains("98765 43210"));
        assertFalse(safe.contains("https://example.com"));
        assertTrue(safe.contains("[redacted]"));
    }

    @Test
    void boundsProviderContextText() {
        String safe = AiContextTextSanitizer.sanitize("x".repeat(1500));
        assertTrue(safe.length() <= 1000);
    }
}
