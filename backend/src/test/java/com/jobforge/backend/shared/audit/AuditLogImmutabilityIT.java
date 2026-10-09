package com.jobforge.backend.shared.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobforge.backend.support.ApiIntegrationTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

/** DATABASE_SCHEMA §4.6 / REQ-20261001: audit_logs is append-only at the privilege AND trigger level. Requires Docker. */
class AuditLogImmutabilityIT extends ApiIntegrationTestSupport {

    private void insertRow(UUID id) {
        jdbc.update("INSERT INTO core.audit_logs (id, source, action, entity_type) VALUES (?, 'BACKEND', 'TEST_ACTION', 'Test')", id);
    }

    @Test
    void backendRoleHasNoUpdateOrDeletePrivilege() {
        assertThat(privilege("UPDATE")).isFalse();
        assertThat(privilege("DELETE")).isFalse();
        assertThat(privilege("INSERT")).isTrue();
        assertThat(privilege("SELECT")).isTrue();
    }

    @Test
    void triggerBlocksUpdateAndDeleteEvenForTheOwner() {
        UUID id = UUID.randomUUID();
        insertRow(id);
        assertThatThrownBy(() -> jdbc.update("UPDATE core.audit_logs SET action = 'TAMPERED' WHERE id = ?", id))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM core.audit_logs WHERE id = ?", id))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
        assertThat(jdbc.queryForObject("SELECT action FROM core.audit_logs WHERE id = ?", String.class, id)).isEqualTo("TEST_ACTION");
    }

    @Test
    void outcomeConstraintAndTriggerAreInstalled() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO core.audit_logs (id, source, action, entity_type, outcome) VALUES (?, 'BACKEND', 'X', 'T', 'MAYBE')", UUID.randomUUID()))
                .isInstanceOf(DataAccessException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_trigger WHERE tgname = 'trg_audit_immutable'", Integer.class)).isEqualTo(1);
    }

    @Test
    void registrationAuditRowsCarryNoSecrets() throws Exception {
        Account a = register("JOB_SEEKER");
        String rows = jdbc.queryForObject(
                "SELECT string_agg(coalesce(before_state::text,'') || coalesce(after_state::text,'') || coalesce(metadata::text,''), '') "
                        + "FROM core.audit_logs WHERE entity_id = (SELECT id FROM core.users WHERE email = ?)",
                String.class, a.email());
        assertThat(rows == null ? "" : rows).doesNotContain(a.password()).doesNotContain("$2a$").doesNotContain("$2b$");
    }

    private boolean privilege(String privilege) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT has_table_privilege('jobforge_backend', 'core.audit_logs', ?)", Boolean.class, privilege));
    }
}
