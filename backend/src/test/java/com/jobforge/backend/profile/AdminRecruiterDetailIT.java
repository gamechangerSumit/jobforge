package com.jobforge.backend.profile;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobforge.backend.support.ApiIntegrationTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** GET /admin/recruiters/{userId}: authorization negatives, happy path, unknown id. Requires Docker. */
class AdminRecruiterDetailIT extends ApiIntegrationTestSupport {

    private UUID userId(Account a) {
        return jdbc.queryForObject("SELECT id FROM core.users WHERE email = ?", UUID.class, a.email());
    }

    @Test
    void detailIsAdminOnly() throws Exception {
        Account recruiter = register("RECRUITER");
        Session rs = ready(recruiter);
        UUID id = userId(recruiter);
        mvc.perform(get(API + "/admin/recruiters/" + id)).andExpect(status().isUnauthorized());
        mvc.perform(authedGet("/admin/recruiters/" + id, rs)).andExpect(status().isForbidden());
        mvc.perform(authedGet("/admin/recruiters/" + id, ready(register("JOB_SEEKER")))).andExpect(status().isForbidden());
    }

    @Test
    void adminSeesRecruiterDetailAndUnknownIdIs404() throws Exception {
        Account recruiter = register("RECRUITER");
        ready(recruiter);
        Session admin = adminLogin();
        mvc.perform(authedGet("/admin/recruiters/" + userId(recruiter), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(recruiter.email()))
                .andExpect(jsonPath("$.data.approvalStatus").value("PENDING"));
        mvc.perform(authedGet("/admin/recruiters/" + UUID.randomUUID(), admin)).andExpect(status().isNotFound());
    }
}
