package com.jobforge.backend;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobforge.backend.support.TestDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Full-context Phase 0 smoke test: bootstrap, Flyway-per-schema, health probes, error envelope. Requires Docker. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class HealthEndpointIT {

    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        TestDatabase.createSchemas(PG); // infra pre-creates schemas in real environments
        registry.add("spring.datasource.url", PG::getJdbcUrl);
        registry.add("spring.datasource.username", PG::getUsername);
        registry.add("spring.datasource.password", PG::getPassword);
        registry.add("jobforge.migration.url", PG::getJdbcUrl);
        registry.add("jobforge.migration.user", PG::getUsername);
        registry.add("jobforge.migration.password", PG::getPassword);
        registry.add("jobforge.security.jwt.secret", () -> java.util.UUID.randomUUID() + "-" + java.util.UUID.randomUUID());
    }

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void livenessIsUp() {
        ResponseEntity<String> response = rest.getForEntity("/actuator/health/liveness", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    void readinessIsUpWhenDatabaseIsReachable() {
        ResponseEntity<String> response = rest.getForEntity("/actuator/health/readiness", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    void healthDoesNotExposeComponentDetails() {
        ResponseEntity<String> response = rest.getForEntity("/actuator/health", String.class);
        assertThat(response.getBody()).doesNotContain("components").doesNotContain("jdbc:");
    }

    @Test
    void flywayHistoryExistsInEachSchemaAfterStartup() {
        for (String schema : new String[] {"core", "community", "platform"}) {
            Integer count = jdbc.queryForObject(
                    "SELECT count(*) FROM information_schema.tables WHERE table_schema = ? AND table_name = 'flyway_schema_history'",
                    Integer.class, schema);
            assertThat(count).as("history table in %s", schema).isEqualTo(1);
        }
    }

    @Test
    void unauthenticatedApiCallReturnsErrorEnvelopeWithRequestId() {
        ResponseEntity<String> response = rest.getForEntity("/api/v1/does-not-exist", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getFirst("X-Request-Id")).isNotBlank();
        assertThat(response.getBody()).contains("\"code\":\"AUTH_UNAUTHENTICATED\"").contains("\"status\":401");
    }
}
