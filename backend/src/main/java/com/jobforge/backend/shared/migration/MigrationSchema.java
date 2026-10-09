package com.jobforge.backend.shared.migration;

/**
 * Schemas migrated by the backend, in the mandatory order (DATABASE_SCHEMA §2: core → community → platform).
 * The {@code ai} schema is migrated by ai-service.
 */
public enum MigrationSchema {
    CORE("core"),
    COMMUNITY("community"),
    PLATFORM("platform");

    private final String schemaName;

    MigrationSchema(String schemaName) {
        this.schemaName = schemaName;
    }

    public String schemaName() {
        return schemaName;
    }

    /** Flyway location: {@code db/migration/<schema>} (DATABASE_SCHEMA §2). */
    public String location() {
        return "classpath:db/migration/" + schemaName;
    }
}
