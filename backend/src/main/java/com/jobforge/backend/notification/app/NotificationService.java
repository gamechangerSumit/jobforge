package com.jobforge.backend.notification.app;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class NotificationService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public NotificationService(
            JdbcTemplate jdbc,
            ObjectMapper mapper
    ) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public void create(
            UUID userId,
            String type,
            String title,
            String body,
            Map<String, Object> data,
            String dedupeKey
    ) {

        try {

            String json =
                    mapper.writeValueAsString(data);

            jdbc.update(
                    """
                    INSERT INTO platform.notifications
                    (
                        id,
                        user_id,
                        type,
                        title,
                        body,
                        data,
                        dedupe_key,
                        created_at
                    )
                    VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?)
                    """,
                    UUID.randomUUID(),
                    userId,
                    type,
                    title,
                    body,
                    json,
                    dedupeKey,
                    Instant.now()
            );

        } catch (DuplicateKeyException ignored) {
            // Duplicate Kafka delivery / notification.
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Unable to create notification",
                    ex
            );
        }
    }

    public List<NotificationResponse> find(
            UUID userId,
            boolean unread,
            int limit
    ) {

        String condition =
                unread
                        ? "AND read_at IS NULL"
                        : "";

        return jdbc.query(
                """
                SELECT id,
                       type,
                       title,
                       body,
                       data::text,
                       read_at,
                       created_at
                FROM platform.notifications
                WHERE user_id = ?
                %s
                ORDER BY created_at DESC
                LIMIT ?
                """.formatted(condition),
                (rs, row) -> {

                    Map<String, Object> data;

                    try {
                        data =
                                mapper.readValue(
                                        rs.getString("data"),
                                        new TypeReference<>() {}
                                );
                    } catch (Exception ex) {
                        data = Map.of();
                    }

                    return new NotificationResponse(
                            rs.getObject(
                                    "id",
                                    UUID.class
                            ),
                            rs.getString("type"),
                            rs.getString("title"),
                            rs.getString("body"),
                            data,
                            rs.getTimestamp("read_at") == null
                                    ? null
                                    : rs.getTimestamp(
                                            "read_at"
                                    ).toInstant(),
                            rs.getTimestamp(
                                    "created_at"
                            ).toInstant()
                    );
                },
                userId,
                Math.min(Math.max(limit, 1), 100)
        );
    }

    public long unreadCount(
            UUID userId
    ) {

        Long count =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM platform.notifications
                        WHERE user_id = ?
                          AND read_at IS NULL
                        """,
                        Long.class,
                        userId
                );

        return count == null ? 0 : count;
    }

    public void markRead(
            UUID userId,
            UUID notificationId
    ) {

        jdbc.update(
                """
                UPDATE platform.notifications
                SET read_at = COALESCE(read_at, ?)
                WHERE id = ?
                  AND user_id = ?
                """,
                Instant.now(),
                notificationId,
                userId
        );
    }

    public void markAllRead(
            UUID userId
    ) {

        jdbc.update(
                """
                UPDATE platform.notifications
                SET read_at = ?
                WHERE user_id = ?
                  AND read_at IS NULL
                """,
                Instant.now(),
                userId
        );
    }
}