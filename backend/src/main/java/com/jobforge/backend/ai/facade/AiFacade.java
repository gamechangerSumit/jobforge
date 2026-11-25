package com.jobforge.backend.ai.facade;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.UUID;

/** Narrow public API for other modules to verify an AI request without depending on the AI HTTP layer. */
public interface AiFacade {
    /** Runs recruiter candidate matching using trusted backend context assembled by the application module. */
    Map<?, ?> recommendCandidates(JsonNode trustedContext, UUID userId, String role, String traceId);

    /** Returns an AI request only when it belongs to the supplied user identity. */
    Map<?, ?> ownedRequest(UUID requestId, UUID userId, String role, String traceId);
}
