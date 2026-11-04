package com.jobforge.backend.notification.app;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobforge.backend.shared.api.CursorMeta;
import com.jobforge.backend.shared.api.CursorResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class NotificationService {
    private static final List<String> TYPES = List.of(
            "EMAIL_VERIFIED", "APPLICATION_SUBMITTED", "APPLICATION_STATUS_CHANGED", "APPLICATION_RECEIVED",
            "APPLICATION_WITHDRAWN", "INTERVIEW_SCHEDULED", "INTERVIEW_UPDATED", "INTERVIEW_CANCELLED",
            "INTERVIEW_RESPONSE", "JOB_PUBLISHED", "JOB_REMOVED", "JOB_EXPIRING_SOON", "RECRUITER_APPROVED",
            "RECRUITER_REJECTED", "COMPANY_VERIFIED", "COMPANY_REJECTED", "POST_LIKED", "POST_COMMENTED",
            "COMMENT_REPLIED", "USER_MENTIONED", "USER_FOLLOWED", "CONTENT_MODERATED", "AI_QUOTA_LOW",
            "SYSTEM_ANNOUNCEMENT");

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final NotificationCursorCodec cursors;
    private final Map<UUID, Set<SseEmitter>> streams = new ConcurrentHashMap<>();

    public NotificationService(JdbcTemplate jdbc, ObjectMapper mapper, NotificationCursorCodec cursors) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.cursors = cursors;
    }

    public void create(UUID userId, String type, String title, String body, Map<String, Object> data, String dedupeKey) {
        try {
            String json = mapper.writeValueAsString(data);
            UUID id = UUID.randomUUID();
            Instant created = Instant.now();
            jdbc.update("""
                INSERT INTO platform.notifications (id,user_id,type,title,body,data,dedupe_key,created_at)
                VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?)
                """, id, userId, type, title, body, json, dedupeKey, created);
            NotificationResponse response = new NotificationResponse(id, type, title, body, data, null, created);
            Set<SseEmitter> emitters = streams.get(userId);
            if (emitters != null) {
                for (SseEmitter emitter : emitters) {
                    try { emitter.send(SseEmitter.event().name("notification").id(id.toString()).data(response)); }
                    catch (Exception ex) { remove(userId, emitter); }
                }
            }
        } catch (DuplicateKeyException ignored) {
            // Duplicate Kafka delivery / notification.
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to create notification", ex);
        }
    }

    public CursorResponse<NotificationResponse> find(UUID userId, boolean unread, String cursor, int requestedLimit) {
        int limit = Math.min(Math.max(requestedLimit, 1), 50);
        NotificationCursorCodec.Position position = cursors.decode(cursor);
        String unreadClause = unread ? " AND read_at IS NULL" : "";
        String cursorClause = position == null ? "" : " AND (created_at < ? OR (created_at = ? AND id < ?))";
        List<Object> args = new ArrayList<>();
        args.add(userId);
        if (position != null) {
            args.add(position.createdAt());
            args.add(position.createdAt());
            args.add(position.id());
        }
        args.add(limit + 1);
        List<NotificationResponse> rows = jdbc.query("""
            SELECT id,type,title,body,data::text,read_at,created_at
            FROM platform.notifications
            WHERE user_id = ?
            """ + unreadClause + cursorClause + """
            ORDER BY created_at DESC, id DESC
            LIMIT ?
            """, (rs, row) -> {
                Map<String, Object> data;
                try { data = mapper.readValue(rs.getString("data"), new TypeReference<>() {}); }
                catch (Exception ex) { data = Map.of(); }
                return new NotificationResponse(rs.getObject("id", UUID.class), rs.getString("type"),
                        rs.getString("title"), rs.getString("body"), data,
                        rs.getTimestamp("read_at") == null ? null : rs.getTimestamp("read_at").toInstant(),
                        rs.getTimestamp("created_at").toInstant());
            }, args.toArray());
        boolean hasNext = rows.size() > limit;
        if (hasNext) rows.remove(rows.size() - 1);
        String next = hasNext && !rows.isEmpty()
                ? cursors.encode(rows.get(rows.size() - 1).createdAt(), rows.get(rows.size() - 1).id()) : null;
        return new CursorResponse<>(List.copyOf(rows), new CursorMeta(next, hasNext, limit));
    }

    public long unreadCount(UUID userId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM platform.notifications WHERE user_id = ? AND read_at IS NULL",
                Long.class, userId);
        return count == null ? 0 : count;
    }

    public void markRead(UUID userId, UUID notificationId) {
        jdbc.update("UPDATE platform.notifications SET read_at = COALESCE(read_at, ?) WHERE id = ? AND user_id = ?",
                Instant.now(), notificationId, userId);
    }

    public void markAllRead(UUID userId) {
        jdbc.update("UPDATE platform.notifications SET read_at = ? WHERE user_id = ? AND read_at IS NULL",
                Instant.now(), userId);
    }

    public List<NotificationPreference> preferences(UUID userId) {
        Map<String, NotificationPreference> saved = jdbc.query("""
            SELECT type,in_app_enabled,email_enabled FROM platform.notification_preferences WHERE user_id = ?
            """, rs -> {
                Map<String, NotificationPreference> result = new java.util.HashMap<>();
                while (rs.next()) result.put(rs.getString("type"), new NotificationPreference(
                        rs.getString("type"), rs.getBoolean("in_app_enabled"), rs.getBoolean("email_enabled")));
                return result;
            }, userId);
        return TYPES.stream().map(type -> saved.getOrDefault(type, new NotificationPreference(type, true, true))).toList();
    }

    public List<NotificationPreference> updatePreferences(UUID userId, List<NotificationPreference> preferences) {
        if (preferences == null) throw new IllegalArgumentException("Preferences are required.");
        for (NotificationPreference preference : preferences) {
            if (preference == null || !TYPES.contains(preference.type())) {
                throw new IllegalArgumentException("Unknown notification preference type.");
            }
            jdbc.update("""
                INSERT INTO platform.notification_preferences(user_id,type,in_app_enabled,email_enabled)
                VALUES (?,?,?,?)
                ON CONFLICT (user_id,type) DO UPDATE SET
                    in_app_enabled=EXCLUDED.in_app_enabled,email_enabled=EXCLUDED.email_enabled
                """, userId, preference.type(), preference.inApp(), preference.email());
        }
        return preferences(userId);
    }

    public SseEmitter subscribe(UUID userId) {
        SseEmitter emitter = new SseEmitter(0L);
        streams.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet()).add(emitter);
        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(() -> remove(userId, emitter));
        emitter.onError(error -> remove(userId, emitter));
        try { emitter.send(SseEmitter.event().name("ping").data(Map.of("at", Instant.now().toString()))); }
        catch (Exception ex) { remove(userId, emitter); }
        return emitter;
    }

    @Scheduled(fixedRate = 25_000)
    public void heartbeat() {
        streams.forEach((userId, emitters) -> emitters.forEach(emitter -> {
            try { emitter.send(SseEmitter.event().name("ping").data(Map.of("at", Instant.now().toString()))); }
            catch (Exception ex) { remove(userId, emitter); }
        }));
    }

    private void remove(UUID userId, SseEmitter emitter) {
        Set<SseEmitter> emitters = streams.get(userId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) streams.remove(userId, emitters);
        }
    }
}