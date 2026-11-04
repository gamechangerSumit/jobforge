package com.jobforge.backend.notification.app;

import java.util.UUID;
import java.util.function.Consumer;

/** Application-facing port for delivering committed notifications to active live streams. */
public interface NotificationStreamPublisher {
    void publish(UUID userId, NotificationResponse notification);
    AutoCloseable subscribe(UUID userId, Consumer<NotificationResponse> listener);
}