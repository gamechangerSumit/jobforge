package com.jobforge.backend.search;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobforge.backend.support.ApiIntegrationTestSupport;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Regression guard for the "GET /api/v1/jobs returns 403" report (API_CONTRACT 12.4, marked P).
 * Public job discovery must be reachable anonymously, DRAFT jobs must stay invisible (404, never 403), unmapped URLs must
 * be 404 (not a re-secured ERROR dispatch 403) and the recruiter/application routes that share the /jobs prefix must stay
 * protected. Requires Docker.
 */
class PublicJobEndpointsIT extends ApiIntegrationTestSupport {

    @Test
    void publicDiscoveryEndpointsAreAnonymouslyReadable() throws Exception {
        mvc.perform(get(API + "/jobs")).andExpect(status().isOk()).andExpect(jsonPath("$.data").isArray());
        mvc.perform(get(API + "/jobs").param("q", "engineer").param("skills", "java,spring").param("postedWithin", "7d")
                .param("sort", "postedAt,desc").param("page", "0").param("size", "20")).andExpect(status().isOk());
        mvc.perform(get(API + "/jobs/facets")).andExpect(status().isOk());
        mvc.perform(get(API + "/skills").param("q", "ja")).andExpect(status().isOk());
        mvc.perform(get(API + "/jobs/" + UUID.randomUUID() + "/similar")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void searchValidationErrorsAre400NotSecurityErrors() throws Exception {
        mvc.perform(get(API + "/jobs").param("bogus", "1")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        mvc.perform(get(API + "/jobs").param("sort", "title")).andExpect(status().isBadRequest());
        mvc.perform(get(API + "/jobs").param("postedWithin", "1y")).andExpect(status().isBadRequest());
    }

    @Test
    void unmappedAndMissingResourcesAre404Not403() throws Exception {
        mvc.perform(get(API + "/definitely-not-a-route")).andExpect(status().isUnauthorized()); // deny-by-default for anonymous
        Session recruiter = ready(register("RECRUITER"));
        mvc.perform(authedGet("/definitely-not-a-route", recruiter)).andExpect(status().isNotFound());
        mvc.perform(get(API + "/jobs/" + UUID.randomUUID())).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void jobsPrefixRoutesThatAreNotPublicStayProtected() throws Exception {
        UUID someJob = UUID.randomUUID();
        mvc.perform(get(API + "/jobs/" + someJob + "/applications")).andExpect(status().isUnauthorized());
        mvc.perform(get(API + "/recruiters/me/jobs")).andExpect(status().isUnauthorized());
        mvc.perform(postJson("/jobs", Map.of("title", "x"))).andExpect(status().isUnauthorized());
        mvc.perform(delete(API + "/jobs/" + someJob)).andExpect(status().isUnauthorized());

        Session seeker = ready(register("JOB_SEEKER"));
        mvc.perform(authedGet("/recruiters/me/jobs", seeker)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));
        mvc.perform(authed(postJson("/jobs", Map.of("title", "Backend Engineer")), seeker)).andExpect(status().isForbidden());
        mvc.perform(authedGet("/jobs/" + someJob + "/applications", seeker)).andExpect(status().isForbidden());
    }

    @Test
    void draftJobIsInvisibleToAnonymousAndOtherRecruiters() throws Exception {
        Session owner = ready(register("RECRUITER"));
        String company = json.readTree(mvc.perform(authed(postJson("/companies", Map.of(
                        "name", "Visibility Co " + unique(), "industry", "Software")), owner))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).at("/data/id").asText();
        String jobId = json.readTree(mvc.perform(authed(postJson("/jobs", Map.of(
                        "title", "Draft Engineer",
                        "description", "Build and operate reliable services for our hiring platform, working with Java and PostgreSQL daily.",
                        "employmentType", "FULL_TIME", "workMode", "REMOTE", "experienceLevel", "MID")), owner))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).at("/data/id").asText();

        mvc.perform(get(API + "/jobs/" + jobId)).andExpect(status().isNotFound());
        mvc.perform(authedGet("/jobs/" + jobId, ready(register("RECRUITER")))).andExpect(status().isNotFound());
        mvc.perform(authedGet("/jobs/" + jobId, ready(register("JOB_SEEKER")))).andExpect(status().isNotFound());
        mvc.perform(authedGet("/jobs/" + jobId, owner)).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("DRAFT"));
        mvc.perform(get(API + "/companies/" + company)).andExpect(status().isNotFound()); // company still PENDING
    }
}
