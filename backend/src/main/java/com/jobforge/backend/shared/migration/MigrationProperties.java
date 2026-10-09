package com.jobforge.backend.shared.migration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Flyway runs as the owner role ({@code DB_MIGRATION_USER}), never as the runtime role (REQ-20261001).
 * The password has no default and is never logged.
 */
@ConfigurationProperties(prefix = "jobforge.migration")
public record MigrationProperties(@DefaultValue("true") boolean enabled, String url, String user, String password) {

    @Override
    public String toString() {
        return "MigrationProperties[enabled=" + enabled + ", url=" + url + ", user=" + user + ", password=***]";
    }
}
