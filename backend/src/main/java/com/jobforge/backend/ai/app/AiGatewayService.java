package com.jobforge.backend.ai.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobforge.backend.ai.facade.AiFacade;
import com.jobforge.backend.ai.facade.AiGatewayClient;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** AI use-case gateway. Owns orchestration; HTTP transport is hidden behind AiGatewayClient. */
@Service
public class AiGatewayService implements AiFacade {
    private final AiGatewayClient client;

    public AiGatewayService(AiGatewayClient client) {
        this.client = client;
    }

    public Map<?, ?> post(String path, JsonNode body, UUID userId, String role, String requestId, String idempotencyKey) {
        return client.post(path, body, userId, role, requestId, idempotencyKey);
    }

    public Map<?, ?> get(String path, UUID userId, String role, String requestId) {
        return client.get(path, userId, role, requestId);
    }

    public Map<?, ?> put(String path, JsonNode body, UUID userId, String role, String requestId) {
        return client.put(path, body, userId, role, requestId);
    }

    public void delete(String path, UUID userId, String role, String requestId) {
        client.delete(path, userId, role, requestId);
    }

    @Override
    public Map<?, ?> ownedRequest(UUID requestId, UUID userId, String role, String traceId) {
        return client.get("/internal/v1/ai/requests/" + requestId, userId, role, traceId);
    }
}
