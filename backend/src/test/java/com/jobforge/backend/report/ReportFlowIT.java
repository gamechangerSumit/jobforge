package com.jobforge.backend.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobforge.backend.support.ApiIntegrationTestSupport;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Reports and moderation (API_CONTRACT 12.9 POST /reports, 12.11 /admin/reports). Covers filing, visibility
 * (hidden targets are 404), duplicates, self-reports, admin-only access, the resolve decision matrix, terminal state,
 * audit and content effects. Requires Docker. Written but NOT executed in the authoring session.
 */
class ReportFlowIT extends ApiIntegrationTestSupport {

    private record Fixture(Session recruiter, UUID recruiterId, String companyId, String jobId) {}

    private UUID userId(Account account) {
        return jdbc.queryForObject("SELECT id FROM core.users WHERE lower(email) = lower(?)", UUID.class, account.email());
    }

    private String createCompany(Session recruiter) throws Exception {
        return json.readTree(mvc.perform(authed(postJson("/companies", Map.of(
                        "name", "Report Co " + unique(), "industry", "Software")), recruiter))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).at("/data/id").asText();
    }

    private String createDraftJob(Session recruiter) throws Exception {
        return json.readTree(mvc.perform(authed(postJson("/jobs", Map.of(
                        "title", "Reportable Engineer",
                        "description", "Build and operate reliable services for our hiring platform, working with Java and PostgreSQL daily.",
                        "employmentType", "FULL_TIME", "workMode", "REMOTE", "experienceLevel", "MID",
                        "skills", java.util.List.of(Map.of("name", "java")))), recruiter))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).at("/data/id").asText();
    }

    /** Approved recruiter, VERIFIED company, PUBLISHED job. */
    private Fixture publishedJob(Session admin) throws Exception {
        Account account = register("RECRUITER");
        Session recruiter = ready(account);
        UUID recruiterId = userId(account);
        String company = createCompany(recruiter);
        mvc.perform(authed(postJson("/admin/recruiters/" + recruiterId + "/approve", Map.of()), admin)).andExpect(status().isNoContent());
        mvc.perform(authed(postJson("/admin/companies/" + company + "/verify", Map.of()), admin)).andExpect(status().isNoContent());
        String jobId = createDraftJob(recruiter);
        mvc.perform(authed(postJson("/jobs/" + jobId + "/publish", Map.of()), recruiter)).andExpect(status().isOk());
        return new Fixture(recruiter, recruiterId, company, jobId);
    }

    private Map<String, Object> body(String type, Object id, String reason) {
        return Map.of("targetType", type, "targetId", id.toString(), "reason", reason);
    }

    private String fileReport(Session reporter, String type, Object id, String reason, String details) throws Exception {
        Map<String, Object> request = new java.util.HashMap<>(body(type, id, reason));
        if (details != null) request.put("details", details);
        var result = mvc.perform(authed(postJson("/reports", request), reporter))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(header().exists("Location"))
                .andReturn().getResponse();
        String reportId = json.readTree(result.getContentAsString()).at("/data/id").asText();
// API_CONTRACT 4: ...
        assertEquals("/api/v1/admin/reports/" + reportId, java.net.URI.create(result.getHeader("Location")).getPath());
        return reportId;
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder resolve(String reportId, String action, String reason) throws Exception {
        return postJson("/admin/reports/" + reportId + "/resolve", Map.of("action", action, "reason", reason));
    }

    // ------------------------------------------------------------------ filing

    @Test
    void filingRequiresAuthenticationAndValidInput() throws Exception {
        UUID someId = UUID.randomUUID();
        mvc.perform(postJson("/reports", body("JOB", someId, "SPAM"))).andExpect(status().isUnauthorized());

        Session seeker = ready(register("JOB_SEEKER"));
        mvc.perform(authed(postJson("/reports", Map.of("targetType", "JOB")), seeker)).andExpect(status().isBadRequest());
        mvc.perform(authed(postJson("/reports", body("NOPE", someId, "SPAM")), seeker)).andExpect(status().isBadRequest());
        mvc.perform(authed(postJson("/reports", body("JOB", someId, "NOPE")), seeker)).andExpect(status().isBadRequest());
        mvc.perform(authed(postJson("/reports", Map.of("targetType", "JOB", "targetId", someId.toString(),
                "reason", "SPAM", "details", "x".repeat(1001))), seeker)).andExpect(status().isBadRequest());
        // community content cannot be validated until the community module registers its target ports
        mvc.perform(authed(postJson("/reports", body("POST", someId, "SPAM")), seeker)).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("BUSINESS_RULE_VIOLATED"));
        mvc.perform(authed(postJson("/reports", body("COMMENT", someId, "SPAM")), seeker)).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void reportingAPublishedJobWorksOncePerReporterAndIsAudited() throws Exception {
        Session admin = adminLogin();
        Fixture f = publishedJob(admin);
        Session seekerA = ready(register("JOB_SEEKER"));
        Session seekerB = ready(register("JOB_SEEKER"));
        int auditBefore = auditCount("REPORT_FILED");

        mvc.perform(authed(postJson("/reports", Map.of("targetType", "JOB", "targetId", f.jobId(), "reason", "SCAM",
                        "details", "  Asks for money upfront  ")), seekerA))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.details").doesNotExist()); // private text is never echoed
        mvc.perform(authed(postJson("/reports", body("JOB", f.jobId(), "SCAM")), seekerA)).andExpect(status().isConflict());
        mvc.perform(authed(postJson("/reports", body("JOB", f.jobId(), "SPAM")), seekerA)).andExpect(status().isConflict());
        mvc.perform(authed(postJson("/reports", body("JOB", f.jobId(), "SCAM")), seekerB)).andExpect(status().isCreated());

        assertEquals(auditBefore + 2, auditCount("REPORT_FILED"));
    }

    @Test
    void hiddenOrMissingTargetsAreIndistinguishable404s() throws Exception {
        Session admin = adminLogin();
        Account account = register("RECRUITER");
        Session recruiter = ready(account);
        String pendingCompany = createCompany(recruiter);           // PENDING => not public
        String draftJob = createDraftJob(recruiter);                // DRAFT   => not public
        Session seeker = ready(register("JOB_SEEKER"));

        mvc.perform(authed(postJson("/reports", body("JOB", draftJob, "SPAM")), seeker)).andExpect(status().isNotFound());
        mvc.perform(authed(postJson("/reports", body("COMPANY", pendingCompany, "SPAM")), seeker)).andExpect(status().isNotFound());
        mvc.perform(authed(postJson("/reports", body("JOB", UUID.randomUUID(), "SPAM")), seeker)).andExpect(status().isNotFound());
        mvc.perform(authed(postJson("/reports", body("COMPANY", UUID.randomUUID(), "SPAM")), seeker)).andExpect(status().isNotFound());
        mvc.perform(authed(postJson("/reports", body("USER", UUID.randomUUID(), "SPAM")), seeker)).andExpect(status().isNotFound());

        // a job that was removed by an admin is no longer reportable either
        Fixture f = publishedJob(admin);
        String first = fileReport(seeker, "JOB", f.jobId(), "SPAM", null);
        mvc.perform(authed(resolve(first, "REMOVE_CONTENT", "Violates policy"), admin)).andExpect(status().isOk());
        mvc.perform(authed(postJson("/reports", body("JOB", f.jobId(), "SPAM")), ready(register("JOB_SEEKER"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void usersCannotReportThemselvesOrTheirOwnContent() throws Exception {
        Session admin = adminLogin();
        Fixture f = publishedJob(admin);
        mvc.perform(authed(postJson("/reports", body("JOB", f.jobId(), "SPAM")), f.recruiter())).andExpect(status().isUnprocessableEntity());
        mvc.perform(authed(postJson("/reports", body("COMPANY", f.companyId(), "SPAM")), f.recruiter())).andExpect(status().isUnprocessableEntity());
        mvc.perform(authed(postJson("/reports", body("USER", f.recruiterId(), "SPAM")), f.recruiter())).andExpect(status().isUnprocessableEntity());
    }

    // ------------------------------------------------------------------ admin access

    @Test
    void adminEndpointsAreAdminOnly() throws Exception {
        Session admin = adminLogin();
        Fixture f = publishedJob(admin);
        Session seeker = ready(register("JOB_SEEKER"));
        String reportId = fileReport(seeker, "JOB", f.jobId(), "SPAM", "private note");

        for (Session s : new Session[] {seeker, f.recruiter()}) {
            mvc.perform(authedGet("/admin/reports", s)).andExpect(status().isForbidden());
            mvc.perform(authedGet("/admin/reports/" + reportId, s)).andExpect(status().isForbidden());
            mvc.perform(authed(resolve(reportId, "DISMISS", "nope"), s)).andExpect(status().isForbidden());
        }
        mvc.perform(get(API + "/admin/reports")).andExpect(status().isUnauthorized());
        mvc.perform(postJson("/admin/reports/" + reportId + "/resolve", Map.of("action", "DISMISS", "reason", "x")))
                .andExpect(status().isUnauthorized());
        // the reporter has no read endpoint for reports in the contract: another id/route must not leak anything
        mvc.perform(authedGet("/reports/" + reportId, seeker)).andExpect(status().is4xxClientError());
    }

    @Test
    void queueDetailFiltersAndSortWhitelist() throws Exception {
        Session admin = adminLogin();
        Fixture f = publishedJob(admin);
        Session seeker = ready(register("JOB_SEEKER"));
        String reportId = fileReport(seeker, "JOB", f.jobId(), "HARASSMENT", "reporter free text");

        mvc.perform(authedGet("/admin/reports?status=OPEN&targetType=JOB&reason=HARASSMENT&sort=createdAt,desc&size=100", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[?(@.id=='" + reportId + "')]").isNotEmpty())
                .andExpect(jsonPath("$.data[0].details").doesNotExist()); // queue rows carry no reporter text
        mvc.perform(authedGet("/admin/reports?status=RESOLVED&size=100", admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id=='" + reportId + "')]").isEmpty());
        mvc.perform(authedGet("/admin/reports?bogus=1", admin)).andExpect(status().isBadRequest());
        mvc.perform(authedGet("/admin/reports?status=NOPE", admin)).andExpect(status().isBadRequest());
        mvc.perform(authedGet("/admin/reports?sort=reason,asc", admin)).andExpect(status().isBadRequest());
        mvc.perform(authedGet("/admin/reports?size=101", admin)).andExpect(status().isBadRequest());

        mvc.perform(authedGet("/admin/reports/" + reportId, admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.details").value("reporter free text"))
                .andExpect(jsonPath("$.data.target.label").value("Reportable Engineer"))
                .andExpect(jsonPath("$.data.target.available").value(true))
                .andExpect(jsonPath("$.data.reporter.id").exists())
                .andExpect(jsonPath("$.data.actions").isEmpty());
        mvc.perform(authedGet("/admin/reports/" + UUID.randomUUID(), admin)).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ decisions

    @Test
    void dismissIsTerminalAndAudited() throws Exception {
        Session admin = adminLogin();
        Fixture f = publishedJob(admin);
        Session seeker = ready(register("JOB_SEEKER"));
        String reportId = fileReport(seeker, "JOB", f.jobId(), "SPAM", null);
        int resolvedBefore = auditCount("REPORT_RESOLVED");
        int moderatedBefore = auditCount("CONTENT_MODERATED");

        mvc.perform(authed(resolve(reportId, "DISMISS", "No violation found"), admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISMISSED"))
                .andExpect(jsonPath("$.data.actions[0].action").value("DISMISS"));
        mvc.perform(authed(resolve(reportId, "REMOVE_CONTENT", "changed my mind"), admin)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVALID_STATE_TRANSITION"));

        assertEquals(resolvedBefore + 1, auditCount("REPORT_RESOLVED"));
        assertEquals(moderatedBefore, auditCount("CONTENT_MODERATED")); // dismissal changes no content
        assertEquals("PUBLISHED", jdbc.queryForObject("SELECT status FROM core.jobs WHERE id = ?::uuid", String.class, f.jobId()));
        // after dismissal the reporter may file again (unique index only covers OPEN/REVIEWING)
        mvc.perform(authed(postJson("/reports", body("JOB", f.jobId(), "SPAM")), seeker)).andExpect(status().isCreated());
    }

    @Test
    void removeContentRemovesTheJobAndPublishesAuditTrail() throws Exception {
        Session admin = adminLogin();
        Fixture f = publishedJob(admin);
        Session seeker = ready(register("JOB_SEEKER"));
        String reportId = fileReport(seeker, "JOB", f.jobId(), "SCAM", null);
        int moderatedBefore = auditCount("CONTENT_MODERATED");
        int jobRemovedBefore = auditCount("JOB_REMOVED");

        mvc.perform(authed(resolve(reportId, "REMOVE_CONTENT", "Fraudulent posting"), admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESOLVED"))
                .andExpect(jsonPath("$.data.actions[0].action").value("REMOVE_CONTENT"));

        assertEquals("REMOVED", jdbc.queryForObject("SELECT status FROM core.jobs WHERE id = ?::uuid", String.class, f.jobId()));
        assertEquals(moderatedBefore + 1, auditCount("CONTENT_MODERATED"));
        assertEquals(jobRemovedBefore + 1, auditCount("JOB_REMOVED"));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM core.moderation_actions WHERE report_id = ?::uuid", Integer.class, reportId));
        mvc.perform(get(API + "/jobs/" + f.jobId())).andExpect(status().isNotFound()); // removed content stays hidden
    }

    @Test
    void unsupportedActionsAreRejectedWithoutSideEffects() throws Exception {
        Session admin = adminLogin();
        Fixture f = publishedJob(admin);
        Session seeker = ready(register("JOB_SEEKER"));
        String reportId = fileReport(seeker, "JOB", f.jobId(), "SPAM", null);

        mvc.perform(authed(resolve(reportId, "HIDE_CONTENT", "Not applicable here"), admin)).andExpect(status().isUnprocessableEntity());
        mvc.perform(authed(resolve(reportId, "SUSPEND_USER", "Not applicable here"), admin)).andExpect(status().isUnprocessableEntity());
        mvc.perform(authed(postJson("/admin/reports/" + reportId + "/resolve", Map.of("action", "DISMISS", "reason", "  ")), admin))
                .andExpect(status().isBadRequest());
        mvc.perform(authed(postJson("/admin/reports/" + reportId + "/resolve", Map.of("action", "BAN", "reason", "x")), admin))
                .andExpect(status().isBadRequest());

        mvc.perform(authedGet("/admin/reports/" + reportId, admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OPEN")).andExpect(jsonPath("$.data.actions").isEmpty());
        assertEquals("PUBLISHED", jdbc.queryForObject("SELECT status FROM core.jobs WHERE id = ?::uuid", String.class, f.jobId()));
    }

    @Test
    void warnUserResolvesWithoutChangingTheContent() throws Exception {
        Session admin = adminLogin();
        Fixture f = publishedJob(admin);
        Session seeker = ready(register("JOB_SEEKER"));
        String reportId = fileReport(seeker, "COMPANY", f.companyId(), "MISINFORMATION", null);

        mvc.perform(authed(resolve(reportId, "WARN_USER", "Misleading company description"), admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESOLVED"));
        assertEquals("VERIFIED", jdbc.queryForObject("SELECT verification_status FROM core.companies WHERE id = ?::uuid", String.class, f.companyId()));
        // companies have no contract-defined removal/suspension outcome
        String second = fileReport(ready(register("JOB_SEEKER")), "COMPANY", f.companyId(), "SPAM", null);
        mvc.perform(authed(resolve(second, "REMOVE_CONTENT", "Not applicable here"), admin)).andExpect(status().isUnprocessableEntity());
        mvc.perform(authed(resolve(second, "SUSPEND_USER", "Not applicable here"), admin)).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void suspendUserSuspendsTheReportedAccountButNeverAnAdministrator() throws Exception {
        Session admin = adminLogin();
        Account offender = register("JOB_SEEKER");
        ready(offender);
        UUID offenderId = userId(offender);
        Session reporter = ready(register("JOB_SEEKER"));

        String reportId = fileReport(reporter, "USER", offenderId, "HARASSMENT", null);
        mvc.perform(authed(resolve(reportId, "SUSPEND_USER", "Repeated harassment"), admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESOLVED"));
        assertEquals("SUSPENDED", jdbc.queryForObject("SELECT status FROM core.users WHERE id = ?", String.class, offenderId));

        // a suspended account is no longer visible to reporters
        mvc.perform(authed(postJson("/reports", body("USER", offenderId, "SPAM")), ready(register("JOB_SEEKER"))))
                .andExpect(status().isNotFound());

        // administrators cannot be suspended through a report either (the user module's rule applies)
        Account otherAdmin = register("JOB_SEEKER");
        verifyEmail(otherAdmin);
        jdbc.update("DELETE FROM core.seeker_profiles WHERE user_id = (SELECT id FROM core.users WHERE email = ?)", otherAdmin.email());
        jdbc.update("UPDATE core.users SET role = 'ADMIN' WHERE email = ?", otherAdmin.email());
        String adminReport = fileReport(reporter, "USER", userId(otherAdmin), "SPAM", null);
        mvc.perform(authed(resolve(adminReport, "SUSPEND_USER", "Not applicable here"), admin)).andExpect(status().isUnprocessableEntity());
        mvc.perform(authedGet("/admin/reports/" + adminReport, admin)).andExpect(jsonPath("$.data.status").value("OPEN"));
    }

    // ------------------------------------------------- S3-01: reason length is enforced after trimming (NOT RUN)

    @Test
    void resolveReasonMustBeTenToFiveHundredCharactersAfterTrimming() throws Exception {
        Session admin = adminLogin();
        Fixture f = publishedJob(admin);
        Session seeker = ready(register("JOB_SEEKER"));
        String reportId = fileReport(seeker, "JOB", f.jobId(), "SPAM", null);

        String nine = "123456789";
        String paddedShort = "     a" + " ".repeat(20);              // 1 character after trimming
        String paddedNine = "   " + nine + "   ";                    // 9 characters after trimming
        String fiveHundredOne = "a".repeat(501);
        for (String bad : new String[] {nine, paddedShort, paddedNine, fiveHundredOne}) {
            mvc.perform(authed(resolve(reportId, "DISMISS", bad), admin)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        }
        // rejected requests leave the report untouched
        mvc.perform(authedGet("/admin/reports/" + reportId, admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OPEN")).andExpect(jsonPath("$.data.actions").isEmpty());

        // exactly 10 characters after trimming is accepted, and the stored reason is the trimmed value
        mvc.perform(authed(resolve(reportId, "DISMISS", "  1234567890  "), admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISMISSED"));
        assertEquals("1234567890", jdbc.queryForObject(
                "SELECT reason FROM core.moderation_actions WHERE report_id = ?::uuid", String.class, reportId));

        // exactly 500 characters is accepted, also when it is padded with whitespace beyond 500 raw characters
        String second = fileReport(ready(register("JOB_SEEKER")), "JOB", f.jobId(), "SPAM", null);
        String fiveHundred = "b".repeat(500);
        mvc.perform(authed(resolve(second, "DISMISS", "  " + fiveHundred + "  "), admin)).andExpect(status().isOk());
        assertEquals(500, jdbc.queryForObject(
                "SELECT length(reason) FROM core.moderation_actions WHERE report_id = ?::uuid", Integer.class, second));
    }

    // ------------------------------------- S3-02: soft-deleted but still VERIFIED company is missing (NOT RUN)

    @Test
    void softDeletedVerifiedCompanyIsNotReportableAndOnlyDismissable() throws Exception {
        Session admin = adminLogin();
        Fixture f = publishedJob(admin);
        Session seeker = ready(register("JOB_SEEKER"));
        // a report filed while the company was still public
        String existing = fileReport(seeker, "COMPANY", f.companyId(), "SPAM", null);

        jdbc.update("UPDATE core.companies SET deleted_at = now() WHERE id = ?::uuid", f.companyId());
        assertEquals("VERIFIED", jdbc.queryForObject(
                "SELECT verification_status FROM core.companies WHERE id = ?::uuid", String.class, f.companyId()));

        // new filings behave exactly like a missing target (404, no ID probing)
        mvc.perform(authed(postJson("/reports", body("COMPANY", f.companyId(), "SCAM")), ready(register("JOB_SEEKER"))))
                .andExpect(status().isNotFound());

        // the earlier report shows the target as unavailable; only DISMISS is permitted
        mvc.perform(authedGet("/admin/reports/" + existing, admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.target.available").value(false));
        mvc.perform(authed(resolve(existing, "WARN_USER", "Warning the company owner"), admin))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(authed(resolve(existing, "DISMISS", "Company no longer exists"), admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("DISMISSED"));
    }

    // ------------- report details: raw UTF-16 length checked before trimming, JS-trim blank handling (NOT RUN)

    @Test
    void detailsAreCheckedRawThenTrimmedLikeJavaScript() throws Exception {
        Session admin = adminLogin();
        Fixture f = publishedJob(admin);
        String emoji = "\uD83D\uDE00";

        // rejected: 1001 units, padding that pushes the raw value over 1000, emoji counted as 2 units each
        for (String bad : new String[] {"x".repeat(1001), "x".repeat(1000) + "  ", "\u00A0" + "x".repeat(1000),
                emoji.repeat(501)}) {
            Map<String, Object> request = new java.util.HashMap<>(body("JOB", f.jobId(), "SPAM"));
            request.put("details", bad);
            mvc.perform(authed(postJson("/reports", request), ready(register("JOB_SEEKER"))))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        }

        // accepted and stored trimmed: exactly 1000 units (ASCII and emoji), padded valid text
        String thousand = fileReport(ready(register("JOB_SEEKER")), "JOB", f.jobId(), "SPAM", "x".repeat(1000));
        assertEquals(1000, jdbc.queryForObject("SELECT length(details) FROM core.reports WHERE id = ?::uuid",
                Integer.class, thousand));
        String emojis = fileReport(ready(register("JOB_SEEKER")), "JOB", f.jobId(), "SPAM", emoji.repeat(500));
        assertEquals(500, jdbc.queryForObject("SELECT length(details) FROM core.reports WHERE id = ?::uuid",
                Integer.class, emojis)); // 500 code points = 1000 UTF-16 units
        String padded = fileReport(ready(register("JOB_SEEKER")), "JOB", f.jobId(), "SPAM", "\u00A0\uFEFF  hi \u3000");
        assertEquals("hi", jdbc.queryForObject("SELECT details FROM core.reports WHERE id = ?::uuid",
                String.class, padded));

        // values JavaScript trim() considers blank are omitted (stored as none), like empty details
        for (String blank : new String[] {"", "   ", "\u00A0\u00A0", "\uFEFF", "\u3000\u3000"}) {
            String id = fileReport(ready(register("JOB_SEEKER")), "JOB", f.jobId(), "SPAM", blank);
            assertNull(jdbc.queryForObject("SELECT details FROM core.reports WHERE id = ?::uuid", String.class, id));
        }
        String none = fileReport(ready(register("JOB_SEEKER")), "JOB", f.jobId(), "SPAM", null);
        assertNull(jdbc.queryForObject("SELECT details FROM core.reports WHERE id = ?::uuid", String.class, none));
    }
}
