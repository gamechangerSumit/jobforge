package com.jobforge.backend.analytics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobforge.backend.support.ApiIntegrationTestSupport;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

/**
 * Proposed, unmerged Session 5 contract change (§12.10.1 / §12.11.2) end to end: calculations, status distribution, funnel, date boundaries, empty
 * results, join inflation, tenant isolation, authorization negatives and invalid input. Requires Docker. Written but
 * NOT executed in the authoring session.
 */
class AnalyticsFlowIT extends ApiIntegrationTestSupport {

    private record Company(Session recruiter, String jobId) {}

    private Company company(Session admin) throws Exception {
        Account account = register("RECRUITER");
        Session recruiter = ready(account);
        String companyId = json.readTree(mvc.perform(authed(postJson("/companies", Map.of("name", "Stats Co " + unique(),
                        "industry", "Software")), recruiter)).andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString()).at("/data/id").asText();
        UUID userId = jdbc.queryForObject("SELECT id FROM core.users WHERE email = ?", UUID.class, account.email());
        mvc.perform(authed(postJson("/admin/recruiters/" + userId + "/approve", Map.of()), admin)).andExpect(status().isNoContent());
        mvc.perform(authed(postJson("/admin/companies/" + companyId + "/verify", Map.of()), admin)).andExpect(status().isNoContent());
        String jobId = json.readTree(mvc.perform(authed(postJson("/jobs", Map.of(
                        "title", "Analytics Engineer",
                        "description", "Build and operate reliable services for our hiring platform, working with Java and PostgreSQL daily.",
                        "employmentType", "FULL_TIME", "workMode", "REMOTE", "experienceLevel", "MID",
                        "skills", List.of(Map.of("name", "Java", "required", true)))), recruiter))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).at("/data/id").asText();
        mvc.perform(authed(postJson("/jobs/" + jobId + "/publish", Map.of()), recruiter)).andExpect(status().isOk());
        return new Company(recruiter, jobId);
    }

    /** Applies as a fresh seeker and returns the application id. */
    private String apply(String jobId) throws Exception {
        Session seeker = ready(register("JOB_SEEKER"));
        MockMultipartFile file = new MockMultipartFile("file", "cv.pdf", "application/pdf",
                "%PDF-1.4\n1 0 obj<<>>endobj\ntrailer<<>>\n%%EOF".getBytes(StandardCharsets.US_ASCII));
        String resumeId = json.readTree(mvc.perform(authed(multipart(API + "/seekers/me/resumes").file(file), seeker))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).at("/data/id").asText();
        return json.readTree(mvc.perform(authed(postJson("/jobs/" + jobId + "/applications", Map.of("resumeId", resumeId))
                                .header("Idempotency-Key", UUID.randomUUID().toString()), seeker))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).at("/data/id").asText();
    }

    private void move(Session recruiter, String applicationId, String to) throws Exception {
        mvc.perform(authed(patch(API + "/applications/" + applicationId + "/status").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("status", to, "reason", "analytics test"))), recruiter))
                .andExpect(status().isOk());
    }

    private JsonNode overview(Session s, String query) throws Exception {
        return json.readTree(mvc.perform(authedGet("/recruiters/me/analytics/overview" + query, s)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).at("/data");
    }

    // ------------------------------------------------------------ calculations

    @Test
    void overviewAndJobFunnelReflectStatusChangesWithoutInflation() throws Exception {
        Session admin = adminLogin();
        Company c = company(admin);
        String a1 = apply(c.jobId());
        String a2 = apply(c.jobId());
        String a3 = apply(c.jobId());
        for (String s : List.of("UNDER_REVIEW", "SHORTLISTED", "INTERVIEW", "OFFERED", "HIRED")) move(c.recruiter(), a1, s);
        move(c.recruiter(), a2, "UNDER_REVIEW");
        move(c.recruiter(), a2, "REJECTED");
        // a3 stays SUBMITTED. Notes and extra history rows must not change any count.
        mvc.perform(authed(postJson("/applications/" + a3 + "/notes", Map.of("body", "first")), c.recruiter()));
        mvc.perform(authed(postJson("/applications/" + a3 + "/notes", Map.of("body", "second")), c.recruiter()));

        JsonNode o = overview(c.recruiter(), "");
        assertEquals(3, o.at("/applications").asInt());
        assertEquals(1, o.at("/pipeline/HIRED").asInt());
        assertEquals(1, o.at("/pipeline/REJECTED").asInt());
        assertEquals(1, o.at("/pipeline/SUBMITTED").asInt());
        assertEquals(0, o.at("/pipeline/WITHDRAWN").asInt());
        assertEquals(0.3333, o.at("/conversion/rate").asDouble(), 0.00001);
        assertEquals(1, o.at("/timeToHire/hired").asInt());
        assertTrue(o.at("/timeToHire/averageDays").asDouble() >= 0);
        assertEquals(1, o.at("/jobs/published").asInt());
        assertEquals(1, o.at("/jobs/open").asInt());

        JsonNode j = json.readTree(mvc.perform(authedGet("/recruiters/me/analytics/jobs/" + c.jobId(), c.recruiter()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).at("/data");
        assertEquals(3, j.at("/applications").asInt());
        assertEquals(3, j.at("/funnel/0/count").asInt());   // SUBMITTED
        assertEquals(2, j.at("/funnel/1/count").asInt());   // UNDER_REVIEW reached by a1, a2
        assertEquals(1, j.at("/funnel/2/count").asInt());   // SHORTLISTED
        assertEquals(1, j.at("/funnel/5/count").asInt());   // HIRED
        assertEquals(1, j.at("/outcomes/REJECTED").asInt());
        assertEquals(30, j.at("/series").size());
        long total = 0;
        for (JsonNode p : j.at("/series")) total += p.at("/applications").asLong();
        assertEquals(3, total);
    }

    @Test
    void hiredWithoutHiredHistoryCountsForConversionButNotTimeToHire() throws Exception {
        Company c = company(adminLogin());
        String applicationId = apply(c.jobId());
        jdbc.update("UPDATE core.applications SET status = 'HIRED' WHERE id = ?::uuid", applicationId);
        jdbc.update("DELETE FROM core.application_status_history WHERE application_id = ?::uuid AND to_status = 'HIRED'", applicationId);

        JsonNode o = overview(c.recruiter(), "");

        assertEquals(1, o.at("/pipeline/HIRED").asInt());
        assertEquals(1, o.at("/conversion/hired").asInt());
        assertEquals(1.0, o.at("/conversion/rate").asDouble());
        assertEquals(0, o.at("/timeToHire/hired").asInt());
        assertTrue(o.at("/timeToHire/averageDays").isNull());
        assertTrue(o.at("/timeToHire/medianDays").isNull());
    }

    @Test
    void emptyCompanyReturnsZerosAndNulls() throws Exception {
        Company c = company(adminLogin());
        JsonNode o = overview(c.recruiter(), "");
        assertEquals(0, o.at("/applications").asInt());
        assertTrue(o.at("/conversion/rate").isNull());
        assertTrue(o.at("/timeToHire/averageDays").isNull());
        assertEquals(8, o.at("/pipeline").size());
    }

    // ------------------------------------------------------- date boundaries

    @Test
    void rangeBoundariesAreInclusiveUtcDays() throws Exception {
        Company c = company(adminLogin());
        String early = apply(c.jobId());
        String late = apply(c.jobId());
        jdbc.update("UPDATE core.applications SET applied_at = TIMESTAMPTZ '2026-09-30 23:59:59+00' WHERE id = ?::uuid", early);
        jdbc.update("UPDATE core.applications SET applied_at = TIMESTAMPTZ '2026-10-01 00:00:00+00' WHERE id = ?::uuid", late);

        assertEquals(1, overview(c.recruiter(), "?from=2026-10-01&to=2026-10-01").at("/applications").asInt());
        assertEquals(1, overview(c.recruiter(), "?from=2026-09-30&to=2026-09-30").at("/applications").asInt());
        assertEquals(2, overview(c.recruiter(), "?from=2026-09-30&to=2026-10-01").at("/applications").asInt());
        assertEquals(0, overview(c.recruiter(), "?from=2026-10-02&to=2026-10-03").at("/applications").asInt());
    }

    // -------------------------------------------------------- tenant isolation

    @Test
    void anotherCompanyNeverSeesOrProbesMyData() throws Exception {
        Session admin = adminLogin();
        Company a = company(admin);
        Company b = company(admin);
        apply(a.jobId());
        apply(a.jobId());

        assertEquals(2, overview(a.recruiter(), "").at("/applications").asInt());
        assertEquals(0, overview(b.recruiter(), "").at("/applications").asInt());
        mvc.perform(authedGet("/recruiters/me/analytics/jobs/" + a.jobId(), b.recruiter()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
        mvc.perform(authedGet("/recruiters/me/analytics/jobs/" + UUID.randomUUID(), a.recruiter())).andExpect(status().isNotFound());
    }

    @Test
    void softDeletedJobsAreExcluded() throws Exception {
        Company c = company(adminLogin());
        apply(c.jobId());
        jdbc.update("UPDATE core.jobs SET deleted_at = now() WHERE id = ?::uuid", c.jobId());
        assertEquals(0, overview(c.recruiter(), "").at("/applications").asInt());
        mvc.perform(authedGet("/recruiters/me/analytics/jobs/" + c.jobId(), c.recruiter())).andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------- authorization

    @Test
    void authorizationNegatives() throws Exception {
        Session admin = adminLogin();
        Session seeker = ready(register("JOB_SEEKER"));
        Company c = company(admin);
        String[] recruiterPaths = {"/recruiters/me/analytics/overview", "/recruiters/me/analytics/jobs/" + c.jobId()};
        for (String path : recruiterPaths) {
            mvc.perform(get(API + path)).andExpect(status().isUnauthorized());
            mvc.perform(authedGet(path, seeker)).andExpect(status().isForbidden());
            mvc.perform(authedGet(path, admin)).andExpect(status().isForbidden());
        }
        String[] adminPaths = {"/admin/analytics/overview", "/admin/analytics/timeseries?metric=USERS_REGISTERED"};
        for (String path : adminPaths) {
            mvc.perform(get(API + path)).andExpect(status().isUnauthorized());
            mvc.perform(authedGet(path, seeker)).andExpect(status().isForbidden());
            mvc.perform(authedGet(path, c.recruiter())).andExpect(status().isForbidden());
        }
    }

    @Test
    void unapprovedRecruiterIsForbidden() throws Exception {
        Session pending = ready(register("RECRUITER"));
        mvc.perform(authedGet("/recruiters/me/analytics/overview", pending))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("RECRUITER_NOT_APPROVED"));
    }

    // --------------------------------------------------------------- validation

    @Test
    void invalidRangesAndFiltersAre400() throws Exception {
        Company c = company(adminLogin());
        for (String q : List.of("?from=2026-10-05&to=2026-10-01", "?from=nope", "?to=2026-13-01", "?from=2024-01-01&to=2026-10-01",
                "?unknown=1")) {
            mvc.perform(authedGet("/recruiters/me/analytics/overview" + q, c.recruiter()))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        }
    }

    // -------------------------------------------------------------------- admin

    @Test
    void adminOverviewAndTimeseries() throws Exception {
        Session admin = adminLogin();
        Company c = company(admin);
        apply(c.jobId());
        apply(c.jobId());

        JsonNode o = json.readTree(mvc.perform(authedGet("/admin/analytics/overview", admin)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).at("/data");
        assertTrue(o.at("/applications/submitted").asInt() >= 2);
        assertTrue(o.at("/users/registered").asInt() >= 3);
        assertTrue(o.at("/jobs/currentlyOpen").asInt() >= 1);

        JsonNode t = json.readTree(mvc.perform(authedGet("/admin/analytics/timeseries?metric=APPLICATIONS_SUBMITTED&interval=DAY", admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).at("/data");
        assertEquals("APPLICATIONS_SUBMITTED", t.at("/metric").asText());
        assertEquals(30, t.at("/points").size());
        long total = 0;
        for (JsonNode p : t.at("/points")) total += p.at("/value").asLong();
        assertTrue(total >= 2);

        for (String q : List.of("", "?metric=BOGUS", "?metric=USERS_REGISTERED&interval=YEAR",
                "?metric=USERS_REGISTERED&from=2026-10-05&to=2026-10-01", "?metric=USERS_REGISTERED&extra=1")) {
            mvc.perform(authedGet("/admin/analytics/timeseries" + q, admin))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        }
    }
}
