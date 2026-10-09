package com.jobforge.backend.shared.events;

/**
 * Outbound port for domain events. MUST be called inside the business transaction. Production binding is Dev 3's
 * transactional outbox ({@code OutboxPublisher}); until that adapter exists {@link EventPublisherConfig} supplies a
 * no-op so business flows keep working (see docs/requests/REQ-20261005-phase2-integration.md).
 * Feature code never uses KafkaTemplate directly.
 */
public interface EventPublisher {

    void publish(DomainEvent event);
}
