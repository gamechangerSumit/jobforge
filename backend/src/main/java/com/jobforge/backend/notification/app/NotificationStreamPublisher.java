package com.jobforge.backend.notification.app;

import java.util.UUID;

/** Application-facing port for delivering a committed notification to active live streams. */
public interface NotificationStreamPublisher {
    void publish(UUID userId, NotificationResponse notification);
}