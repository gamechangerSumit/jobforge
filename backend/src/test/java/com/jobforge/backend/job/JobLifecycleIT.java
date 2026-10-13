
        package com.jobforge.backend.job;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobforge.backend.support.ApiIntegrationTestSupport;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Job publish gating and optimistic locking (If-Match on publish/unpublish/close).
 * Requires Docker.
 */
class JobLifecycleIT extends ApiIntegrationTestSupport {

    private record Fixture(
            Session recruiter,
            Session admin,
            String companyId,
            String jobId,
            long version) {}

    private Fixture approvedRecruiterWithDraftJob() throws Exception {
        Account account = register("RECRUITER");
        Session recruiter = ready(account);

        String company = json.readTree(
                        mvc.perform(authed(postJson("/companies", Map.of(
                                        "name", "Lifecycle Co " + unique(),
                                        "industry", "Software")), recruiter))
                                .andExpect(status().isCreated())
                                .andReturn()
                                .getResponse()
                                .getContentAsString())
                .at("/data/id").asText();

        Session admin = adminLogin();

        UUID userId = jdbc.queryForObject(
                "SELECT id FROM core.users WHERE email = ?",
                UUID.class,
                account.email());

        mvc.perform(authed(
                        postJson("/admin/recruiters/" + userId + "/approve", Map.of()),
                        admin))
                .andExpect(status().isNoContent());

        mvc.perform(authed(
                        postJson("/admin/companies/" + company + "/verify", Map.of()),
                        admin))
                .andExpect(status().isNoContent());

        String created = mvc.perform(authed(postJson("/jobs", Map.of(
                        "title", "Backend Engineer",
                        "description",
                        "Build and operate reliable services for our hiring platform, working with Java and PostgreSQL daily.",
                        "employmentType", "FULL_TIME",
                        "workMode", "REMOTE",
                        "experienceLevel", "MID",
                        "skills", List.of(
                                Map.of("name", "Java", "required", true),
                                Map.of("name", "PostgreSQL", "required", true)
                        )
                )), recruiter))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        var node = json.readTree(created).at("/data");

        return new Fixture(
                recruiter,
                admin,
                company,
                node.get("id").asText(),
                node.get("version").asLong());
    }

    @Test
    void publishWithStaleIfMatchIsRejectedAndCurrentVersionSucceeds() throws Exception {
        Fixture f = approvedRecruiterWithDraftJob();

        mvc.perform(authed(
                        postJson("/jobs/" + f.jobId() + "/publish", Map.of())
                                .header("If-Match", "\"" + (f.version() + 5) + "\""),
                        f.recruiter()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("STALE_VERSION"));

        mvc.perform(authed(
                        postJson("/jobs/" + f.jobId() + "/publish", Map.of())
                                .header("If-Match", "\"" + f.version() + "\""),
                        f.recruiter()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));
    }

    @Test
    void publishWithoutIfMatchStillWorksAndOthersCannotTransition() throws Exception {
        Fixture f = approvedRecruiterWithDraftJob();

        Session stranger = ready(register("RECRUITER"));

        mvc.perform(authed(
                        postJson("/jobs/" + f.jobId() + "/publish", Map.of()),
                        stranger))
                .andExpect(status().isNotFound());

        mvc.perform(postJson("/jobs/" + f.jobId() + "/publish", Map.of()))
                .andExpect(status().isUnauthorized());

        mvc.perform(authed(
                        postJson("/jobs/" + f.jobId() + "/publish", Map.of()),
                        f.recruiter()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));
    }
}
