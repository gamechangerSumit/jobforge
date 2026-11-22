package com.jobforge.backend.ai.facade;

import java.util.Map;
import java.util.UUID;

/** Narrow public API for other modules to verify an AI request without depending on the AI HTTP layer. */
public interface AiFacade {
    /** Returns an AI request only when it belongs to the supplied user identity. */
    Map<?, ?> ownedRequest(UUID requestId, UUID userId, String role, String traceId);
}
