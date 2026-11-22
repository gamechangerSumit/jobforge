package com.jobforge.backend.notification.api;

import com.jobforge.backend.notification.app.NotificationResponse;
import com.jobforge.backend.notification.app.NotificationStreamPublisher;
import com.jobforge.backend.shared.security.AuthenticatedUser;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Authenticated SSE transport. The application layer only sees a transport-neutral publisher port. */
@RestController
@RequestMapping("/notifications")
public class NotificationStreamController {
    private final NotificationStreamPublisher publisher;
    private final Map<UUID, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public NotificationStreamController(NotificationStreamPublisher publisher) {
        this.publisher = publisher;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@AuthenticationPrincipal AuthenticatedUser caller) {
        UUID userId = caller.id();
        SseEmitter emitter = new SseEmitter(0L);
        emitters.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet()).add(emitter);
        AutoCloseable subscription = publisher.subscribe(userId, notification -> {
            try {
                emitter.send(SseEmitter.event().name("notification").id(notification.id().toString()).data(notification));
            } catch (Exception ex) {
                emitter.completeWithError(ex);
            }
        });
        Runnable cleanup = () -> {
            try { subscription.close(); } catch (Exception ignored) { /* Best-effort listener cleanup. */ }
            Set<SseEmitter> active = emitters.get(userId);
            if (active != null) {
                active.remove(emitter);
                if (active.isEmpty()) emitters.remove(userId, active);
            }
        };
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(error -> cleanup.run());
        try { emitter.send(SseEmitter.event().name("ping").data(Map.of("at", Instant.now().toString()))); }
        catch (Exception ex) { cleanup.run(); }
        return emitter;
    }

    @Scheduled(fixedRate = 25_000)
    public void heartbeat() {
        emitters.forEach((userId, active) -> active.forEach(emitter -> {
            try { emitter.send(SseEmitter.event().name("ping").data(Map.of("at", Instant.now().toString()))); }
            catch (Exception ex) { emitter.completeWithError(ex); }
        }));
    }
}