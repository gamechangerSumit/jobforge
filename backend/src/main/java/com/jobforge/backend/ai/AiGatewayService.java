package com.jobforge.backend.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobforge.backend.shared.error.ApiException;
import com.jobforge.backend.shared.error.ErrorCode;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/** Backend-only client for ai-service. Browser access tokens are never forwarded. */
@Service
public class AiGatewayService {
    private final RestClient client;
    private final ObjectMapper mapper;
    private final String internalToken;

    public AiGatewayService(RestClient.Builder builder, ObjectMapper mapper,
            @Value("${jobforge.ai.base-url:http://localhost:8090}") String baseUrl,
            @Value("${jobforge.ai.internal-token:}") String internalToken) {
        this.client = builder.baseUrl(baseUrl).build();
        this.mapper = mapper;
        this.internalToken = internalToken;
    }

    public Map<?, ?> post(String path, JsonNode body, UUID userId, String role, String requestId, String idempotencyKey) {
        if (internalToken == null || internalToken.isBlank()) {
            throw new ApiException(ErrorCode.AI_PROVIDER_UNAVAILABLE, "AI service is not configured.");
        }
        try {
            return client.post().uri(path)
                    .header("X-Internal-Token", internalToken)
                    .header("X-User-Id", userId.toString())
                    .header("X-User-Role", role)
                    .header("X-Request-Id", requestId == null ? UUID.randomUUID().toString() : requestId)
                    .headers(h -> { if (idempotencyKey != null && !idempotencyKey.isBlank()) h.set("Idempotency-Key", idempotencyKey); })
                    .body(body)
                    .retrieve().body(Map.class);
        } catch (RestClientResponseException ex) {
            throw translate(ex);
        } catch (ResourceAccessException ex) {
            throw new ApiException(ErrorCode.AI_PROVIDER_UNAVAILABLE, "AI service is temporarily unavailable.");
        }
    }

    public Map<?, ?> get(String path, UUID userId, String role, String requestId) {
        if (internalToken == null || internalToken.isBlank()) {
            throw new ApiException(ErrorCode.AI_PROVIDER_UNAVAILABLE, "AI service is not configured.");
        }
        try {
            return client.get().uri(path)
                    .header("X-Internal-Token", internalToken)
                    .header("X-User-Id", userId.toString())
                    .header("X-User-Role", role)
                    .header("X-Request-Id", requestId == null ? UUID.randomUUID().toString() : requestId)
                    .retrieve().body(Map.class);
        } catch (RestClientResponseException ex) {
            throw translate(ex);
        } catch (ResourceAccessException ex) {
            throw new ApiException(ErrorCode.AI_PROVIDER_UNAVAILABLE, "AI service is temporarily unavailable.");
        }
    }

    private ApiException translate(RestClientResponseException ex) {
        String code = "AI_PROVIDER_UNAVAILABLE";
        String message = "AI service could not complete the request.";
        try {
            JsonNode root = mapper.readTree(ex.getResponseBodyAsString());
            JsonNode error = root.path("error");
            if (error.hasNonNull("code")) code = error.path("code").asText();
            if (error.hasNonNull("message")) message = error.path("message").asText();
        } catch (Exception ignored) {
            // Never expose raw upstream response bodies.
        }
        ErrorCode mapped;
        try { mapped = ErrorCode.valueOf(code); }
        catch (IllegalArgumentException ignored) {
            HttpStatusCode status = ex.getStatusCode();
            mapped = status.value() == 429 ? ErrorCode.AI_QUOTA_EXCEEDED
                    : status.value() == 400 ? ErrorCode.VALIDATION_FAILED
                    : status.value() == 403 ? ErrorCode.ACCESS_DENIED
                    : status.value() == 404 ? ErrorCode.RESOURCE_NOT_FOUND
                    : status.value() == 504 ? ErrorCode.AI_TIMEOUT
                    : ErrorCode.AI_PROVIDER_UNAVAILABLE;
        }
        return new ApiException(mapped, message);
    }
}
