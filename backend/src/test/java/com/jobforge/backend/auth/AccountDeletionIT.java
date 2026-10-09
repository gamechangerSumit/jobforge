package com.jobforge.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobforge.backend.support.ApiIntegrationTestSupport;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** DELETE /users/me: password-confirmed anonymization, admin refusal, company-owner policy (REQ-20261009). */
class AccountDeletionIT extends ApiIntegrationTestSupport {

    private MockHttpServletRequestBuilder deleteMe(Session session, String password) throws Exception {
        return authed(delete(API + "/users/me").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("password", password))), session);
    }

    @Test
    void anonymousIsUnauthorized() throws Exception {
        mvc.perform(delete(API + "/users/me").contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"x\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongPasswordDoesNotDelete() throws Exception {
        Account account = register("JOB_SEEKER");
        Session session = ready(account);
        mvc.perform(deleteMe(session, "Wrong-" + unique())).andExpect(status().is4xxClientError());
        assertThat(loginResult(account).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void seekerCanDeleteAndCanNoLongerLogIn() throws Exception {
        Account account = register("JOB_SEEKER");
        Session session = ready(account);
        mvc.perform(deleteMe(session, account.password())).andExpect(status().isNoContent());
        assertThat(loginResult(account).getResponse().getStatus()).isEqualTo(401);
        assertThat(auditCount("USER_DELETED")).isGreaterThanOrEqualTo(1);
    }

    @Test
    void adminAccountsAreRefused() throws Exception {
        Account admin = register("JOB_SEEKER");
        verifyEmail(admin);
        jdbc.update("DELETE FROM core.seeker_profiles WHERE user_id = (SELECT id FROM core.users WHERE email = ?)", admin.email());
        jdbc.update("UPDATE core.users SET role = 'ADMIN' WHERE email = ?", admin.email());
        Session session = login(admin);
        mvc.perform(deleteMe(session, admin.password())).andExpect(status().is4xxClientError());
        assertThat(loginResult(admin).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void soleOwnerWithoutOpenJobsCanDeleteAndMembershipIsReleased() throws Exception {
        Account recruiter = register("RECRUITER");
        Session session = ready(recruiter);
        String body = mvc.perform(authed(postJson("/companies", Map.of("name", "Del Co " + unique())), session))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String companyId = json.readTree(body).at("/data/id").asText();

        jdbc.update("UPDATE core.companies SET verification_status = 'VERIFIED' WHERE id = ?::uuid", companyId);
        Integer open = jdbc.queryForObject("SELECT count(*) FROM core.jobs WHERE company_id = ?::uuid AND status = 'PUBLISHED'",
                Integer.class, companyId);
        assertThat(open).isZero(); // no open jobs yet -> deletion allowed

        mvc.perform(deleteMe(session, recruiter.password())).andExpect(status().isNoContent());
        Integer members = jdbc.queryForObject("SELECT count(*) FROM core.company_members WHERE company_id = ?::uuid",
                Integer.class, companyId);
        assertThat(members).isZero(); // membership released
    }
}
