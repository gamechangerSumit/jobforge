package com.jobforge.backend.ai.facade;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.UUID;

/** Outbound port used by the AI application service; the HTTP implementation lives in infrastructure. */
public interface AiGatewayClient {
    Map<?, ?> post(String path, JsonNode body, UUID userId, String role, String requestId, String idempotencyKey);
    Map<?, ?> get(String path, UUID userId, String role, String requestId);
    Map<?, ?> put(String path, JsonNode body, UUID userId, String role, String requestId);
    void delete(String path, UUID userId, String role, String requestId);
}
