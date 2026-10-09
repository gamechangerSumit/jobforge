package com.jobforge.backend.company;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobforge.backend.support.ApiIntegrationTestSupport;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Company onboarding, public visibility rules, moderation and the public job-search regression (was 403). */
class CompanyFlowIT extends ApiIntegrationTestSupport {

    private String createCompany(Session recruiter, String name) throws Exception {
        String body = mvc.perform(authed(postJson("/companies", Map.of("name", name, "industry", "Software")), recruiter))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.verificationStatus").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).at("/data/id").asText();
    }

    @Test
    void publicJobSearchIsAnonymousAndNotForbidden() throws Exception {
        mvc.perform(get(API + "/jobs")).andExpect(status().isOk());
        mvc.perform(get(API + "/jobs/facets")).andExpect(status().isOk());
        mvc.perform(get(API + "/skills").param("q", "ja")).andExpect(status().isOk());
        mvc.perform(get(API + "/jobs").param("workMode", "MOON")).andExpect(status().isBadRequest());
    }

    @Test
    void recruiterCreatesCompanyOnceAndOthersCannot() throws Exception {
        Session recruiter = ready(register("RECRUITER"));
        createCompany(recruiter, "Flow Co " + unique());

        mvc.perform(authedGet("/companies/me", recruiter))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memberRole").value("OWNER"));

        mvc.perform(authed(postJson("/companies", Map.of("name", "Second " + unique())), recruiter))
                .andExpect(status().isConflict());

        Session seeker = ready(register("JOB_SEEKER"));
        mvc.perform(authed(postJson("/companies", Map.of("name", "Nope " + unique())), seeker))
                .andExpect(status().isForbidden());
        mvc.perform(authedGet("/companies/me", seeker)).andExpect(status().isForbidden());
        mvc.perform(postJson("/companies", Map.of("name", "Anon " + unique()))).andExpect(status().isUnauthorized());
    }

    @Test
    void recruiterWithoutCompanyGetsNotFoundOnMe() throws Exception {
        Session recruiter = ready(register("RECRUITER"));
        mvc.perform(authedGet("/companies/me", recruiter)).andExpect(status().isNotFound());
    }

    @Test
    void unverifiedCompanyIsHiddenFromPublicUntilAdminVerifies() throws Exception {
        Session recruiter = ready(register("RECRUITER"));
        String companyId = createCompany(recruiter, "Hidden Co " + unique());

        mvc.perform(get(API + "/companies/" + companyId)).andExpect(status().isNotFound());
        mvc.perform(authedGet("/companies/" + companyId, recruiter)).andExpect(status().isOk());

        Session other = ready(register("RECRUITER"));
        mvc.perform(authedGet("/companies/" + companyId, other)).andExpect(status().isNotFound());

        mvc.perform(authed(postJson("/admin/companies/" + companyId + "/verify", Map.of()), recruiter))
                .andExpect(status().isForbidden());
        mvc.perform(postJson("/admin/companies/" + companyId + "/verify", Map.of())).andExpect(status().isUnauthorized());

        Session admin = adminSession();
        mvc.perform(authed(postJson("/admin/companies/" + companyId + "/verify", Map.of()), admin))
                .andExpect(status().isNoContent());

        mvc.perform(get(API + "/companies/" + companyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verified").value(true))
                .andExpect(jsonPath("$.data.rejectionReason").doesNotExist());
        Integer events = jdbc.queryForObject(
                "SELECT count(*) FROM platform.outbox_events WHERE event_type = 'CompanyVerified'", Integer.class);
        org.assertj.core.api.Assertions.assertThat(events).isGreaterThanOrEqualTo(1);
    }

    @Test
    void onlyOwnerCanAddMembersAndEachRecruiterBelongsToOneCompany() throws Exception {
        Session owner = ready(register("RECRUITER"));
        String companyId = createCompany(owner, "Team Co " + unique());
        Account teammate = register("RECRUITER");
        Session teammateSession = ready(teammate);

        mvc.perform(authed(postJson("/companies/" + companyId + "/members", Map.of("email", teammate.email())), owner))
                .andExpect(status().isCreated());
        mvc.perform(authed(postJson("/companies/" + companyId + "/members", Map.of("email", teammate.email())), owner))
                .andExpect(status().isConflict());

        Account third = register("RECRUITER");
        ready(third);
        mvc.perform(authed(postJson("/companies/" + companyId + "/members", Map.of("email", third.email())), teammateSession))
                .andExpect(status().isForbidden());
    }

    private Session adminSession() throws Exception {
        Account admin = register("JOB_SEEKER");
        verifyEmail(admin);
        jdbc.update("DELETE FROM core.seeker_profiles WHERE user_id = (SELECT id FROM core.users WHERE email = ?)", admin.email());
        jdbc.update("UPDATE core.users SET role = 'ADMIN' WHERE email = ?", admin.email());
        return login(admin);
    }

}
