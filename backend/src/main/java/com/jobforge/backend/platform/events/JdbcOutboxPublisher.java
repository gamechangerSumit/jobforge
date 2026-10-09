package com.jobforge.backend.platform.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobforge.backend.shared.persistence.Db;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class JdbcOutboxPublisher implements OutboxPublisher {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public JdbcOutboxPublisher(
            JdbcTemplate jdbc,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID publish(
            String topic,
            String aggregateType,
            UUID aggregateId,
            String eventType,
            int eventVersion,
            Object payload,
            EventActor actor,
            String traceId
    ) {
        UUID eventId = UUID.randomUUID();
        Instant occurredAt = clock.instant();

        Map<String, Object> headers = new LinkedHashMap<>();
        headers.put("eventId", eventId.toString());
        headers.put("eventType", eventType);
        headers.put("eventVersion", eventVersion);
        headers.put("occurredAt", occurredAt.toString());
        headers.put("producer", "backend");
        headers.put("aggregateType", aggregateType);
        headers.put("traceId", traceId);

        if (actor != null) {
            Map<String, Object> actorHeader = new LinkedHashMap<>();
            actorHeader.put("userId", actor.userId() == null ? null : actor.userId().toString());
            actorHeader.put("role", actor.role() == null ? null : actor.role().name());
            headers.put("actor", actorHeader);
        }

        jdbc.update(
                """
                INSERT INTO platform.outbox_events
                    (
                        id,
                        aggregate_type,
                        aggregate_id,
                        event_type,
                        event_version,
                        topic,
                        payload,
                        headers,
                        created_at
                    )
                VALUES
                    (?, ?, ?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), ?)
                """,
                eventId,
                aggregateType,
                aggregateId,
                eventType,
                eventVersion,
                topic,
                json(payload),
                json(headers),
                Db.ts(occurredAt)
        );

        return eventId;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Unable to serialize outbox event payload",
                    e
            );
        }
    }
}