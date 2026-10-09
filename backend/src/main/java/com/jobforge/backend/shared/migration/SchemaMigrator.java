package com.jobforge.backend.shared.migration;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One Flyway run per schema (core, then community, then platform), each with its own history table
 * inside that schema and {@code createSchemas=false} (schemas are pre-created by infrastructure; REQ-20261001).
 *
 * <p>Locations may legitimately be empty/missing (e.g. {@code platform} is delivered by Dev 3), so missing
 * locations do not fail the run. Phase 1 note: JPA's EntityManagerFactory must be made to depend on the
 * {@code schemaMigrator} bean when entities are introduced.
 */
public class SchemaMigrator {

    private static final Logger log = LoggerFactory.getLogger(SchemaMigrator.class);

    private final String url;
    private final String user;
    private final String password;

    public SchemaMigrator(String url, String user, String password) {
        if (isBlank(url) || isBlank(user) || isBlank(password)) {
            throw new IllegalStateException(
                    "Flyway migration requires DB_URL, DB_MIGRATION_USER and DB_MIGRATION_PASSWORD to be set");
        }
        this.url = url;
        this.user = user;
        this.password = password;
    }

    /** Invoked as the Spring bean init method. */
    public void migrateAll() {
        for (MigrationSchema schema : MigrationSchema.values()) {
            migrate(schema);
        }
    }

    void migrate(MigrationSchema schema) {
        String name = schema.schemaName();
        Flyway.configure()
                .dataSource(url, user, password)
                .defaultSchema(name)
                .schemas(name)
                .createSchemas(false)
                .table("flyway_schema_history")
                .locations(schema.location())
                .failOnMissingLocations(false)
                .validateOnMigrate(true)
                .cleanDisabled(true)
                .connectRetries(5)
                .load()
                .migrate();
        log.info("Flyway migration finished for schema '{}'", name);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
