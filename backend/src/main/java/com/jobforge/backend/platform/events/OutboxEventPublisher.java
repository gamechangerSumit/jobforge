package com.jobforge.backend.platform.events;

import com.jobforge.backend.shared.events.DomainEvent;
import com.jobforge.backend.shared.events.EventPublisher;
import com.jobforge.backend.shared.web.ClientInfo;
import org.springframework.stereotype.Component;

/**
 * Production binding of the EventPublisher port (ARCHITECTURE 14). Every domain event is written to
 * platform.outbox_events inside the business transaction of the caller and relayed to Kafka by OutboxRelay.
 * Feature code never touches Kafka directly.
 */
@Component
public class OutboxEventPublisher implements EventPublisher {

    private static final int EVENT_VERSION = 1;

    private final OutboxPublisher outbox;

    public OutboxEventPublisher(OutboxPublisher outbox) {
        this.outbox = outbox;
    }

    @Override
    public void publish(DomainEvent event) {
        EventActor actor = event.actorUserId() == null && event.actorRole() == null
                ? null
                : new EventActor(event.actorUserId(), event.actorRole());
        outbox.publish(event.topic(), event.aggregateType(), event.aggregateId(), event.eventType(), EVENT_VERSION,
                event.payload(), actor, ClientInfo.current().requestId());
    }
}
