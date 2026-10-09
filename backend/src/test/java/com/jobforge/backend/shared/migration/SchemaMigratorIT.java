package com.jobforge.backend.shared.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobforge.backend.support.TestDatabase;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Flyway foundation against an empty PostgreSQL 16 (DATABASE_SCHEMA §16 rule 6). Requires Docker. */
@Testcontainers
class SchemaMigratorIT {

    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16");

    @BeforeEach
    void freshSchemas() {
        TestDatabase.dropSchemas(PG);
        TestDatabase.createSchemas(PG);
    }

    private SchemaMigrator migrator() {
        return new SchemaMigrator(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
    }

    @Test
    void createsOneHistoryTableInsideEachSchema() {
        migrator().migrateAll();

        for (MigrationSchema schema : MigrationSchema.values()) {
            int tables = TestDatabase.count(PG,
                    "SELECT count(*) FROM information_schema.tables WHERE table_schema = '"
                            + schema.schemaName() + "' AND table_name = 'flyway_schema_history'");
            assertThat(tables).as("history table in %s", schema.schemaName()).isEqualTo(1);
        }
        int inPublic = TestDatabase.count(PG,
                "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'flyway_schema_history'");
        assertThat(inPublic).isZero();
    }

    @Test
    void isIdempotent() {
        migrator().migrateAll();
        assertThatCode(() -> migrator().migrateAll()).doesNotThrowAnyException();
    }

    @Test
    void doesNotCreateMissingSchemas() {
        TestDatabase.execute(PG, "DROP SCHEMA community CASCADE");
        assertThatThrownBy(() -> migrator().migrateAll()).isInstanceOf(FlywayException.class);
        int created = TestDatabase.count(PG, "SELECT count(*) FROM information_schema.schemata WHERE schema_name = 'community'");
        assertThat(created).isZero();
    }

    @Test
    void rejectsMissingCredentialsWithoutEchoingThem() {
        assertThatThrownBy(() -> new SchemaMigrator("jdbc:postgresql://x/db", "owner", " "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("owner");
    }

    @Test
    void migrationOrderIsCoreCommunityPlatform() {
        assertThat(MigrationSchema.values())
                .containsExactly(MigrationSchema.CORE, MigrationSchema.COMMUNITY, MigrationSchema.PLATFORM);
        assertThat(MigrationSchema.CORE.location()).isEqualTo("classpath:db/migration/core");
    }
}
