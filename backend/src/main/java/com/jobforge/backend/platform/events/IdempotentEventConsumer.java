package com.jobforge.backend.platform.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Consumer;

@Component
public class IdempotentEventConsumer {

    private final JdbcClient jdbc;
    private final ObjectMapper mapper;

    public IdempotentEventConsumer(
            JdbcClient jdbc,
            ObjectMapper mapper
    ) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Transactional
    public void consume(
            String consumerGroup,
            String rawMessage,
            Consumer<EventEnvelope> handler
    ) {
        try {
            EventEnvelope event =
                    mapper.readValue(rawMessage, EventEnvelope.class);

            int inserted = jdbc.sql("""
                    INSERT INTO platform.processed_events
                        (consumer_group, event_id, processed_at)
                    VALUES
                        (:consumerGroup, :eventId, now())
                    ON CONFLICT (consumer_group, event_id)
                    DO NOTHING
                    """)
                    .param("consumerGroup", consumerGroup)
                    .param("eventId", event.eventId())
                    .update();

            // Already processed → ignore duplicate Kafka delivery.
            if (inserted == 0) {
                return;
            }

            handler.accept(event);

        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to process Kafka event",
                    e
            );
        }
    }
}