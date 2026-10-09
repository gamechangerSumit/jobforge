package com.jobforge.backend.shared.migration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Replaces Spring Boot's single-Flyway auto-configuration (disabled in application.yml). */
@Configuration
@EnableConfigurationProperties(MigrationProperties.class)
public class MigrationConfig {

    @Bean(name = "schemaMigrator", initMethod = "migrateAll")
    @ConditionalOnProperty(prefix = "jobforge.migration", name = "enabled", havingValue = "true", matchIfMissing = true)
    public SchemaMigrator schemaMigrator(MigrationProperties properties) {
        return new SchemaMigrator(properties.url(), properties.user(), properties.password());
    }
}
