package com.jobforge.backend.platform.idempotency;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class ProcessedEventService {

    private final JdbcTemplate jdbc;

    public ProcessedEventService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean tryClaim(
            String consumerGroup,
            UUID eventId
    ) {
        try {
            int inserted = jdbc.update(
                    """
                    INSERT INTO platform.processed_events
                        (consumer_group, event_id, processed_at)
                    VALUES
                        (?, ?, now())
                    """,
                    consumerGroup,
                    eventId
            );

            return inserted == 1;

        } catch (DuplicateKeyException ex) {
            return false;
        }
    }
}