package com.jobforge.backend.notification.infra;

import com.jobforge.backend.notification.app.NotificationResponse;
import com.jobforge.backend.notification.app.NotificationStreamPublisher;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

/** Transport-neutral registry of live notification listeners. */
@Component
public class NotificationSseRegistry implements NotificationStreamPublisher {
    private final ConcurrentHashMap<UUID, Set<Consumer<NotificationResponse>>> listeners = new ConcurrentHashMap<>();

    @Override
    public void publish(UUID userId, NotificationResponse notification) {
        Set<Consumer<NotificationResponse>> subscribers = listeners.get(userId);
        if (subscribers == null) return;
        subscribers.forEach(listener -> listener.accept(notification));
    }

    @Override
    public AutoCloseable subscribe(UUID userId, Consumer<NotificationResponse> listener) {
        listeners.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet()).add(listener);
        return () -> {
            Set<Consumer<NotificationResponse>> subscribers = listeners.get(userId);
            if (subscribers != null) {
                subscribers.remove(listener);
                if (subscribers.isEmpty()) listeners.remove(userId, subscribers);
            }
        };
    }
}