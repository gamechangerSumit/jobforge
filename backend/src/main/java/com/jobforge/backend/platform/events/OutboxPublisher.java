package com.jobforge.backend.platform.events;

import java.util.UUID;

public interface OutboxPublisher {

    UUID publish(
            String topic,
            String aggregateType,
            UUID aggregateId,
            String eventType,
            int eventVersion,
            Object payload,
            EventActor actor,
            String traceId
    );
}