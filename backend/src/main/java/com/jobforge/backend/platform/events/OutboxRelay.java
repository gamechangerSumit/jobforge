package com.jobforge.backend.platform.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.jdbc.core.JdbcTemplate;

@Service
@ConditionalOnProperty(name = "jobforge.platform.outbox.relay-enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

    private final JdbcTemplate jdbc;
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper objectMapper;

    public OutboxRelay(
            JdbcTemplate jdbc,
            KafkaTemplate<String, String> kafka,
            ObjectMapper objectMapper
    ) {
        this.jdbc = jdbc;
        this.kafka = kafka;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${jobforge.platform.outbox.poll-ms:500}")
    @Transactional
    public void relay() {

        List<OutboxRow> rows = jdbc.query(
                """
                SELECT
                    id,
                    aggregate_id,
                    event_type,
                    event_version,
                    topic,
                    payload,
                    headers
                FROM platform.outbox_events
                WHERE published_at IS NULL
                  AND attempts < 20
                ORDER BY created_at
                LIMIT 50
                FOR UPDATE SKIP LOCKED
                """,
                (rs, rowNum) -> new OutboxRow(
                        rs.getObject("id", java.util.UUID.class),
                        rs.getObject("aggregate_id", java.util.UUID.class),
                        rs.getString("event_type"),
                        rs.getInt("event_version"),
                        rs.getString("topic"),
                        rs.getString("payload"),
                        rs.getString("headers")
                )
        );

        for (OutboxRow row : rows) {
            publish(row);
        }
    }

    private void publish(OutboxRow row) {

        try {
            JsonNode payload = objectMapper.readTree(row.payload());
            JsonNode headers = objectMapper.readTree(row.headers());

            var envelope = objectMapper.createObjectNode();

            envelope.put("eventId", row.id().toString());
            envelope.put("eventType", row.eventType());
            envelope.put("eventVersion", row.eventVersion());

            if (headers.has("occurredAt")) {
                envelope.set("occurredAt", headers.get("occurredAt"));
            }

            if (headers.has("producer")) {
                envelope.set("producer", headers.get("producer"));
            }

            if (headers.has("actor")) {
                envelope.set("actor", headers.get("actor"));
            }

            envelope.put("aggregateType", headers.hasNonNull("aggregateType") ? headers.get("aggregateType").asText() : "unknown");
            envelope.put("aggregateId", row.aggregateId().toString());

            if (headers.has("traceId")) {
                envelope.set("traceId", headers.get("traceId"));
            }

            envelope.set("payload", payload);

            kafka.send(
                    row.topic(),
                    row.aggregateId().toString(),
                    objectMapper.writeValueAsString(envelope)
            ).get(10, TimeUnit.SECONDS);

            jdbc.update(
                    """
                    UPDATE platform.outbox_events
                    SET published_at = now(),
                        attempts = attempts + 1,
                        last_error = NULL
                    WHERE id = ?
                    """,
                    row.id()
            );

        } catch (Exception ex) {

            jdbc.update(
                    """
                    UPDATE platform.outbox_events
                    SET attempts = attempts + 1,
                        last_error = ?
                    WHERE id = ?
                    """,
                    truncate(ex.getMessage()),
                    row.id()
            );
        }
    }

    private String truncate(String message) {
        if (message == null) {
            return "Unknown outbox publishing error";
        }

        return message.length() > 500
                ? message.substring(0, 500)
                : message;
    }

    private record OutboxRow(
            java.util.UUID id,
            java.util.UUID aggregateId,
            String eventType,
            int eventVersion,
            String topic,
            String payload,
            String headers
    ) {
    }
}