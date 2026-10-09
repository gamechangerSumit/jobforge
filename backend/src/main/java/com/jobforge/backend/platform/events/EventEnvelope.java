package com.jobforge.backend.platform.events;

import java.time.Instant;
import java.util.UUID;

public record EventEnvelope(
        UUID eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        String producer,
        EventActor actor,
        String aggregateType,
        UUID aggregateId,
        String traceId,
        Object payload
) {
}