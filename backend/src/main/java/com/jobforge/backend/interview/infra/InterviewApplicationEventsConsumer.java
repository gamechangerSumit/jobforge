package com.jobforge.backend.interview.infra;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobforge.backend.interview.app.InterviewService;
import com.jobforge.backend.platform.events.EventEnvelope;
import com.jobforge.backend.platform.events.IdempotentEventConsumer;
import com.jobforge.backend.shared.events.EventTopics;
import java.util.UUID;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Keeps interviews consistent with their application: when an application is withdrawn or rejected, its open interviews
 * are cancelled (the interview module never calls the application module for this and the application module never
 * knows about interviews, so the coupling is event-driven). Payload fields are read generically.
 */
@Component
public class InterviewApplicationEventsConsumer {

    static final String GROUP = "jobforge-interview";

    private final IdempotentEventConsumer consumer;
    private final InterviewService interviews;
    private final ObjectMapper mapper;

    public InterviewApplicationEventsConsumer(IdempotentEventConsumer consumer, InterviewService interviews, ObjectMapper mapper) {
        this.consumer = consumer;
        this.interviews = interviews;
        this.mapper = mapper;
    }

    @KafkaListener(topics = EventTopics.APPLICATIONS, groupId = GROUP)
    public void consume(String rawMessage) {
        consumer.consume(GROUP, rawMessage, this::handle);
    }

    void handle(EventEnvelope event) {
        switch (event.eventType()) {
            case "ApplicationWithdrawn" -> close(event, "APPLICATION_WITHDRAWN");
            case "ApplicationStatusChanged" -> {
                JsonNode payload = mapper.valueToTree(event.payload());
                if ("REJECTED".equals(payload.path("to").asText())) {
                    close(event, "APPLICATION_REJECTED");
                }
            }
            default -> { /* not relevant to interviews */ }
        }
    }

    private void close(EventEnvelope event, String cause) {
        JsonNode payload = mapper.valueToTree(event.payload());
        UUID applicationId = uuid(payload, "applicationId");
        UUID jobId = uuid(payload, "jobId");
        if (applicationId == null || jobId == null) {
            return; // malformed payload: nothing to do (and nothing to retry)
        }
        interviews.cancelOpenInterviewsOfClosedApplication(applicationId, jobId, uuid(payload, "companyId"),
                uuid(payload, "seekerUserId"), cause);
    }

    private static UUID uuid(JsonNode payload, String field) {
        String value = payload.path(field).asText("");
        return value.isBlank() || "null".equals(value) ? null : UUID.fromString(value);
    }
}
