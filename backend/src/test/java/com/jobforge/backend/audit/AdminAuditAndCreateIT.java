package com.jobforge.backend.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobforge.backend.support.ApiIntegrationTestSupport;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** GET /admin/audit-logs and POST /admin/users (authorization negatives + happy paths). */
class AdminAuditAndCreateIT extends ApiIntegrationTestSupport {

    @Test
    void auditLogsAreAdminOnly() throws Exception {
        mvc.perform(get(API + "/admin/audit-logs")).andExpect(status().isUnauthorized());
        mvc.perform(authedGet("/admin/audit-logs", ready(register("JOB_SEEKER")))).andExpect(status().isForbidden());
        mvc.perform(authedGet("/admin/audit-logs", ready(register("RECRUITER")))).andExpect(status().isForbidden());
    }

    @Test
    void adminListsAuditLogsAndFiltersAreWhitelisted() throws Exception {
        Session admin = adminLogin();
        mvc.perform(authedGet("/admin/audit-logs?size=5", admin)).andExpect(status().isOk());
        mvc.perform(authedGet("/admin/audit-logs?bogus=1", admin)).andExpect(status().isBadRequest());
        mvc.perform(authedGet("/admin/audit-logs?from=not-a-date", admin)).andExpect(status().isBadRequest());
    }

    @Test
    void createAdminIsAdminOnlyAndMailsASetPasswordLink() throws Exception {
        String email = "newadmin" + unique() + "@example.test";
        Map<String, String> body = Map.of("email", email, "firstName", "New", "lastName", "Admin");

        mvc.perform(postJson("/admin/users", body)).andExpect(status().isUnauthorized());
        mvc.perform(authed(postJson("/admin/users", body), ready(register("RECRUITER")))).andExpect(status().isForbidden());

        Session admin = adminLogin();
        mvc.perform(authed(postJson("/admin/users", body), admin))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.role").value("ADMIN"))
                .andExpect(jsonPath("$.data.email").value(email));
        assertThat(mailer.resetToken(email)).isNotBlank();

        mvc.perform(authed(postJson("/admin/users", body), admin)).andExpect(status().isConflict()); // duplicate email
    }
}
