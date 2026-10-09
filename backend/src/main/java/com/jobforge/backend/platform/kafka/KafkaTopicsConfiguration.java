package com.jobforge.backend.platform.kafka;

import java.util.List;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declares every topic of ARCHITECTURE 14 (jobforge.{domain}.v1 + .dlq) so that the backend, which subscribes to
 * several of them, never depends on the optional kafka-init container having run. KafkaAdmin creates the missing
 * ones at startup and is a no-op for topics that already exist (infrastructure/kafka/create-topics.sh stays valid).
 */
@Configuration
public class KafkaTopicsConfiguration {

    static final List<String> DOMAINS =
            List.of("users", "jobs", "applications", "interviews", "community", "moderation", "ai", "audit");

    @Bean
    @ConditionalOnProperty(name = "jobforge.kafka.declare-topics", havingValue = "true", matchIfMissing = true)
    public org.springframework.kafka.core.KafkaAdmin.NewTopics jobforgeTopics(
            @Value("${jobforge.kafka.partitions:3}") int partitions,
            @Value("${jobforge.kafka.replication:1}") short replication) {
        NewTopic[] topics = DOMAINS.stream()
                .flatMap(d -> java.util.stream.Stream.of("jobforge." + d + ".v1", "jobforge." + d + ".v1.dlq"))
                .map(name -> TopicBuilder.name(name).partitions(partitions).replicas(replication).build())
                .toArray(NewTopic[]::new);
        return new org.springframework.kafka.core.KafkaAdmin.NewTopics(topics);
    }
}
