package com.jobforge.backend.application;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobforge.backend.support.ApiIntegrationTestSupport;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Application contract + IDOR coverage (API_CONTRACT 12.6, DATABASE_SCHEMA 3.6): idempotent apply, one application per
 * seeker/job, no resubmit after withdrawal, If-Match, state machine, seeker ownership and recruiter company scoping
 * (foreign resources are 404, never 403/200). Requires Docker.
 */
class ApplicationFlowIT extends ApiIntegrationTestSupport {

    private record Setup(Session recruiter, Session seeker, String jobId, String resumeId) {}

    private Setup publishedJobAndSeekerWithResume() throws Exception {
        Account recruiterAccount = register("RECRUITER");
        Session recruiter = ready(recruiterAccount);
        String company = json.readTree(mvc.perform(authed(postJson("/companies", Map.of(
                        "name", "Apply Co " + unique(), "industry", "Software")), recruiter))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).at("/data/id").asText();
        Session admin = adminLogin();
        UUID userId = jdbc.queryForObject("SELECT id FROM core.users WHERE email = ?", UUID.class, recruiterAccount.email());
        mvc.perform(authed(postJson("/admin/recruiters/" + userId + "/approve", Map.of()), admin)).andExpect(status().isNoContent());
        mvc.perform(authed(postJson("/admin/companies/" + company + "/verify", Map.of()), admin)).andExpect(status().isNoContent());
        String jobId = json.readTree(mvc.perform(authed(postJson("/jobs", Map.of(
                        "title", "Platform Engineer",
                        "description", "Build and operate reliable services for our hiring platform, working with Java and PostgreSQL daily.",
                        "employmentType", "FULL_TIME", "workMode", "REMOTE", "experienceLevel", "MID")), recruiter))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).at("/data/id").asText();
        mvc.perform(authed(postJson("/jobs/" + jobId + "/publish", Map.of()), recruiter)).andExpect(status().isOk());

        Session seeker = ready(register("JOB_SEEKER"));
        MockMultipartFile file = new MockMultipartFile("file", "cv.pdf", "application/pdf",
                "%PDF-1.4\n1 0 obj<<>>endobj\ntrailer<<>>\n%%EOF".getBytes(StandardCharsets.US_ASCII));
        String resumeId = json.readTree(mvc.perform(authed(multipart(API + "/seekers/me/resumes").file(file), seeker))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).at("/data/id").asText();
        return new Setup(recruiter, seeker, jobId, resumeId);
    }

    private MockHttpServletRequestBuilder apply(Setup s, String idempotencyKey) throws Exception {
        MockHttpServletRequestBuilder b = postJson("/jobs/" + s.jobId() + "/applications", Map.of("resumeId", s.resumeId()));
        return idempotencyKey == null ? b : b.header("Idempotency-Key", idempotencyKey);
    }

    private String applyOk(Setup s) throws Exception {
        return json.readTree(mvc.perform(authed(apply(s, UUID.randomUUID().toString()), s.seeker()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).at("/data/id").asText();
    }

    @Test
    void applyRequiresIdempotencyKeyAndRoleAndRejectsDuplicates() throws Exception {
        Setup s = publishedJobAndSeekerWithResume();
        mvc.perform(apply(s, UUID.randomUUID().toString())).andExpect(status().isUnauthorized());
        mvc.perform(authed(apply(s, null), s.seeker())).andExpect(status().isBadRequest());
        mvc.perform(authed(apply(s, UUID.randomUUID().toString()), s.recruiter())).andExpect(status().isForbidden());

        String key = UUID.randomUUID().toString();
        String id = json.readTree(mvc.perform(authed(apply(s, key), s.seeker())).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).at("/data/id").asText();
        // same key replays the original result, a fresh key is a duplicate application
        mvc.perform(authed(apply(s, key), s.seeker())).andExpect(status().isCreated()).andExpect(jsonPath("$.data.id").value(id));
        mvc.perform(authed(apply(s, UUID.randomUUID().toString()), s.seeker())).andExpect(status().isConflict());
    }

    @Test
    void withdrawnApplicationCannotBeResubmittedAndStaleVersionIsRejected() throws Exception {
        Setup s = publishedJobAndSeekerWithResume();
        String id = applyOk(s);
        mvc.perform(authed(postJson("/applications/" + id + "/withdraw", Map.of()).header("If-Match", "\"99\""), s.seeker()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("STALE_VERSION"));
        mvc.perform(authed(postJson("/applications/" + id + "/withdraw", Map.of()), s.seeker()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("WITHDRAWN"));
        mvc.perform(authed(apply(s, UUID.randomUUID().toString()), s.seeker())).andExpect(status().isConflict());
        // terminal: the recruiter cannot move it any more
        mvc.perform(authed(statusChange(id, "UNDER_REVIEW"), s.recruiter())).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVALID_STATE_TRANSITION"));
    }

    @Test
    void recruiterStateMachineAndIfMatch() throws Exception {
        Setup s = publishedJobAndSeekerWithResume();
        String id = applyOk(s);
        mvc.perform(authed(statusChange(id, "HIRED"), s.recruiter())).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVALID_STATE_TRANSITION"));
        mvc.perform(authed(statusChange(id, "UNDER_REVIEW").header("If-Match", "\"42\""), s.recruiter()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("STALE_VERSION"));
        mvc.perform(authed(statusChange(id, "UNDER_REVIEW"), s.recruiter())).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UNDER_REVIEW"));
        mvc.perform(authed(statusChange(id, "SUBMITTED"), s.recruiter())).andExpect(status().isConflict());
        // a seeker cannot use the recruiter transition endpoint
        mvc.perform(authed(statusChange(id, "SHORTLISTED"), s.seeker())).andExpect(status().isForbidden());
    }

    @Test
    void foreignSeekersAndRecruitersGetNotFoundEverywhere() throws Exception {
        Setup s = publishedJobAndSeekerWithResume();
        String id = applyOk(s);
        Session otherSeeker = ready(register("JOB_SEEKER"));
        Session otherRecruiter = ready(register("RECRUITER")); // no company, not approved

        mvc.perform(authedGet("/applications/" + id, otherSeeker)).andExpect(status().isNotFound());
        mvc.perform(authed(postJson("/applications/" + id + "/withdraw", Map.of()), otherSeeker)).andExpect(status().isNotFound());
        mvc.perform(authedGet("/applications/" + id, s.seeker())).andExpect(status().isOk());
        mvc.perform(authedGet("/applications/" + id, s.recruiter())).andExpect(status().isOk());

        // an unapproved/foreign recruiter must not read, move, rate or annotate candidates
        mvc.perform(authedGet("/applications/" + id, otherRecruiter)).andExpect(status().is4xxClientError());
        mvc.perform(authed(statusChange(id, "UNDER_REVIEW"), otherRecruiter)).andExpect(status().is4xxClientError());
        mvc.perform(authed(rating(id, 5), otherRecruiter)).andExpect(status().is4xxClientError());
        mvc.perform(authed(postJson("/applications/" + id + "/notes", Map.of("body", "leak")), otherRecruiter))
                .andExpect(status().is4xxClientError());
        mvc.perform(authedGet("/jobs/" + s.jobId() + "/applications", otherRecruiter)).andExpect(status().is4xxClientError());

        // seekers never reach recruiter-only data
        mvc.perform(authed(rating(id, 5), s.seeker())).andExpect(status().isForbidden());
        mvc.perform(authedGet("/applications/" + id + "/notes", s.seeker())).andExpect(status().isForbidden());
        mvc.perform(authedGet("/recruiters/me/applications", s.seeker())).andExpect(status().isForbidden());

        // the owning recruiter can rate and annotate; the seeker's list contains only their own applications
        mvc.perform(authed(rating(id, 4), s.recruiter())).andExpect(status().isOk()).andExpect(jsonPath("$.data.rating").value(4));
        mvc.perform(authed(postJson("/applications/" + id + "/notes", Map.of("body", "Strong profile")), s.recruiter()))
                .andExpect(status().isCreated());
        mvc.perform(authedGet("/seekers/me/applications", otherSeeker)).andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
        mvc.perform(authedGet("/seekers/me/applications", s.seeker())).andExpect(status().isOk()).andExpect(jsonPath("$.data[0].id").value(id));
    }

    @Test
    void ratingIsValidatedTo1Through5() throws Exception {
        Setup s = publishedJobAndSeekerWithResume();
        String id = applyOk(s);
        mvc.perform(authed(rating(id, 0), s.recruiter())).andExpect(status().isBadRequest());
        mvc.perform(authed(rating(id, 6), s.recruiter())).andExpect(status().isBadRequest());
        mvc.perform(authed(rating(id, 3), s.recruiter())).andExpect(status().isOk());
    }

    private MockHttpServletRequestBuilder statusChange(String id, String target) throws Exception {
        return patch(API + "/applications/" + id + "/status").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("status", target)));
    }

    private MockHttpServletRequestBuilder rating(String id, int value) throws Exception {
        return put(API + "/applications/" + id + "/rating").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("rating", value)));
    }
}
