package com.jobforge.backend.notification.infra;

import com.jobforge.backend.notification.app.NotificationResponse;
import com.jobforge.backend.notification.app.NotificationStreamPublisher;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** HTTP/SSE adapter for per-user live notification delivery. */
@Component
public class NotificationSseRegistry implements NotificationStreamPublisher {
    private final Map<UUID, Set<SseEmitter>> streams = new ConcurrentHashMap<>();

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

    @Override
    public void publish(UUID userId, NotificationResponse notification) {
        Set<SseEmitter> emitters = streams.get(userId);
        if (emitters == null) return;
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("notification").id(notification.id().toString()).data(notification));
            } catch (Exception ex) {
                remove(userId, emitter);
            }
        }
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