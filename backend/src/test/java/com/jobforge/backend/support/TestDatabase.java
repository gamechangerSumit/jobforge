package com.jobforge.backend.support;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.testcontainers.containers.PostgreSQLContainer;

/** Mirrors what infrastructure/postgres/init/01..03 provide in real environments. */
public final class TestDatabase {

    private TestDatabase() {}

    public static void createSchemas(PostgreSQLContainer<?> pg) {
        execute(pg,
                "CREATE EXTENSION IF NOT EXISTS citext SCHEMA public",
                "CREATE EXTENSION IF NOT EXISTS pg_trgm SCHEMA public",
                "DO $$ BEGIN IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'jobforge_backend') "
                        + "THEN CREATE ROLE jobforge_backend NOLOGIN; END IF; END $$",
                "CREATE SCHEMA IF NOT EXISTS core",
                "CREATE SCHEMA IF NOT EXISTS community",
                "CREATE SCHEMA IF NOT EXISTS platform",
                "GRANT USAGE ON SCHEMA core, community, platform TO jobforge_backend",
                "ALTER DEFAULT PRIVILEGES IN SCHEMA core GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO jobforge_backend",
                "ALTER DEFAULT PRIVILEGES IN SCHEMA community GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO jobforge_backend",
                "ALTER DEFAULT PRIVILEGES IN SCHEMA platform GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO jobforge_backend");
    }

    public static void dropSchemas(PostgreSQLContainer<?> pg) {
        execute(pg, "DROP SCHEMA IF EXISTS core CASCADE",
                "DROP SCHEMA IF EXISTS community CASCADE",
                "DROP SCHEMA IF EXISTS platform CASCADE");
    }

    public static void execute(PostgreSQLContainer<?> pg, String... statements) {
        try (Connection connection = DriverManager.getConnection(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
                Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                statement.execute(sql);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Test database setup failed", e);
        }
    }

    public static int count(PostgreSQLContainer<?> pg, String sql) {
        try (Connection connection = DriverManager.getConnection(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
                Statement statement = connection.createStatement();
                var rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        } catch (SQLException e) {
            throw new IllegalStateException("Test database query failed", e);
        }
    }
}
