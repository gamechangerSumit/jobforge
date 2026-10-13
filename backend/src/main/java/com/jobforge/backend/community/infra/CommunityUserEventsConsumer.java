package com.jobforge.backend.community.infra;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobforge.backend.community.app.AccountCleanupService;
import com.jobforge.backend.platform.events.EventEnvelope;
import com.jobforge.backend.platform.events.IdempotentEventConsumer;
import com.jobforge.backend.shared.events.EventTopics;
import java.util.UUID;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Keeps community data consistent with the user module without coupling them: when an account is deleted
 * ({@code UserDeleted} on {@code jobforge.users.v1}) its personal interactions are purged. The payload is read
 * generically, like the interview module does for application events.
 */
@Component
public class CommunityUserEventsConsumer {

    static final String GROUP = "jobforge-community";

    private final IdempotentEventConsumer consumer;
    private final AccountCleanupService cleanup;
    private final ObjectMapper mapper;

    public CommunityUserEventsConsumer(IdempotentEventConsumer consumer, AccountCleanupService cleanup, ObjectMapper mapper) {
        this.consumer = consumer;
        this.cleanup = cleanup;
        this.mapper = mapper;
    }

    @KafkaListener(topics = EventTopics.USERS, groupId = GROUP)
    public void consume(String rawMessage) {
        consumer.consume(GROUP, rawMessage, this::handle);
    }

    void handle(EventEnvelope event) {
        if (!"UserDeleted".equals(event.eventType())) {
            return; // other user events are not relevant to the community
        }
        JsonNode payload = mapper.valueToTree(event.payload());
        String raw = payload.path("userId").asText("");
        if (raw.isBlank()) {
            return; // malformed payload: nothing to do (and nothing to retry)
        }
        try {
            cleanup.purge(UUID.fromString(raw));
        } catch (IllegalArgumentException e) {
            // malformed id: drop the event instead of retrying forever
        }
    }
}
