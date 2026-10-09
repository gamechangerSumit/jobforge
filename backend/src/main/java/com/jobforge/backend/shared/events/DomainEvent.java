package com.jobforge.backend.shared.events;

import com.jobforge.backend.shared.domain.UserRole;
import java.util.UUID;

/**
 * Module-neutral domain event (ARCHITECTURE §14). The envelope (eventId, occurredAt, traceId, producer) is added by
 * the publisher adapter. {@code payload} must be a plain record; never put secrets, free text or reasons in it.
 */
public record DomainEvent(
        String topic,
        String eventType,
        String aggregateType,
        UUID aggregateId,
        UUID actorUserId,
        UserRole actorRole,
        Object payload) {}
