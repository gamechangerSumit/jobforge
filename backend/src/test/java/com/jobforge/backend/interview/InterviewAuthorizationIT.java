package com.jobforge.backend.interview;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Authentication, role, ownership (seeker) and company-scope (recruiter) rules of API_CONTRACT §12.7, including IDOR.
 * Out-of-scope resources answer 404, never 403, so ids cannot be probed. Requires Docker (not executed in session 1).
 */
class InterviewAuthorizationIT extends InterviewTestSupport {

    private static final Map<String, Object> RESPOND = Map.of("response", "CONFIRM");
    private static final Map<String, Object> CANCEL = Map.of("reason", "Position on hold");
    private static final Map<String, Object> COMPLETE = Map.of("outcome", "COMPLETED");
    private static final Map<String, Object> EDIT = Map.of("durationMinutes", 60);

    private record World(Company company, Seeker seeker, UUID applicationId, UUID interviewId) {}

    private World world() throws Exception {
        Company company = approvedRecruiterWithJob();
        Seeker seeker = verifiedSeeker();
        UUID applicationId = application(seeker, company.jobId(), "SHORTLISTED");
        UUID interviewId = schedule(company.recruiter(), applicationId, inDays(3));
        return new World(company, seeker, applicationId, interviewId);
    }

    // ------------------------------------------------------------------ anonymous

    @Test
    void anonymousCallersAreRejectedEverywhere() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(postJson("/applications/" + id + "/interviews", scheduleBody(inDays(2)))).andExpect(status().isUnauthorized());
        mvc.perform(get(API + "/interviews")).andExpect(status().isUnauthorized());
        mvc.perform(get(API + "/interviews/" + id)).andExpect(status().isUnauthorized());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(API + "/interviews/" + id)
                .contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(postJson("/interviews/" + id + "/cancel", CANCEL)).andExpect(status().isUnauthorized());
        mvc.perform(postJson("/interviews/" + id + "/respond", RESPOND)).andExpect(status().isUnauthorized());
        mvc.perform(postJson("/interviews/" + id + "/complete", COMPLETE)).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ wrong role

    @Test
    void seekersCannotUseRecruiterEndpoints() throws Exception {
        World w = world();
        Session seeker = w.seeker().session();
        mvc.perform(postAs("/applications/" + w.applicationId() + "/interviews", scheduleBody(inDays(5)), seeker))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));
        mvc.perform(patchJson("/interviews/" + w.interviewId(), EDIT, seeker)).andExpect(status().isForbidden());
        mvc.perform(postAs("/interviews/" + w.interviewId() + "/cancel", CANCEL, seeker)).andExpect(status().isForbidden());
        mvc.perform(postAs("/interviews/" + w.interviewId() + "/complete", COMPLETE, seeker)).andExpect(status().isForbidden());
    }

    @Test
    void recruitersCannotRespondOnBehalfOfSeekers() throws Exception {
        World w = world();
        mvc.perform(postAs("/interviews/" + w.interviewId() + "/respond", RESPOND, w.company().recruiter()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));
    }

    @Test
    void adminsAreNotListedInTheContractAndGetForbidden() throws Exception {
        World w = world();
        Session admin = adminLogin();
        mvc.perform(authedGet("/interviews", admin)).andExpect(status().isForbidden());
        mvc.perform(authedGet("/interviews/" + w.interviewId(), admin)).andExpect(status().isForbidden());
        mvc.perform(postAs("/applications/" + w.applicationId() + "/interviews", scheduleBody(inDays(6)), admin)).andExpect(status().isForbidden());
        mvc.perform(patchJson("/interviews/" + w.interviewId(), EDIT, admin)).andExpect(status().isForbidden());
        mvc.perform(postAs("/interviews/" + w.interviewId() + "/cancel", CANCEL, admin)).andExpect(status().isForbidden());
        mvc.perform(postAs("/interviews/" + w.interviewId() + "/respond", RESPOND, admin)).andExpect(status().isForbidden());
        mvc.perform(postAs("/interviews/" + w.interviewId() + "/complete", COMPLETE, admin)).andExpect(status().isForbidden());
    }

    @Test
    void recruitersThatAreNotApprovedAreRefused() throws Exception {
        World w = world();
        Session pending = unapprovedRecruiter();
        mvc.perform(authedGet("/interviews", pending)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("RECRUITER_NOT_APPROVED"));
        mvc.perform(authedGet("/interviews/" + w.interviewId(), pending)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("RECRUITER_NOT_APPROVED"));
        mvc.perform(postAs("/applications/" + w.applicationId() + "/interviews", scheduleBody(inDays(7)), pending))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("RECRUITER_NOT_APPROVED"));
    }

    // ------------------------------------------------------------------ recruiter company scope (IDOR)

    @Test
    void recruiterOfAnotherCompanyCannotReadOrChangeTheInterview() throws Exception {
        World w = world();
        Session outsider = approvedRecruiterWithJob().recruiter();
        mvc.perform(authedGet("/interviews/" + w.interviewId(), outsider)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
        mvc.perform(patchJson("/interviews/" + w.interviewId(), EDIT, outsider)).andExpect(status().isNotFound());
        mvc.perform(postAs("/interviews/" + w.interviewId() + "/cancel", CANCEL, outsider)).andExpect(status().isNotFound());
        mvc.perform(postAs("/interviews/" + w.interviewId() + "/complete", COMPLETE, outsider)).andExpect(status().isNotFound());
        // nothing changed
        mvc.perform(authedGet("/interviews/" + w.interviewId(), w.company().recruiter()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.data.durationMinutes").value(45));
    }

    @Test
    void recruiterOfAnotherCompanyCannotScheduleForForeignApplications() throws Exception {
        World w = world();
        Company other = approvedRecruiterWithJob();
        Seeker candidate = verifiedSeeker();
        UUID foreign = application(candidate, w.company().jobId(), "SHORTLISTED");
        mvc.perform(postAs("/applications/" + foreign + "/interviews", scheduleBody(inDays(4)), other.recruiter()))
                .andExpect(status().isNotFound());
        Integer interviews = jdbc.queryForObject("SELECT count(*) FROM core.interviews WHERE application_id = ?", Integer.class, foreign);
        org.junit.jupiter.api.Assertions.assertEquals(0, interviews);
        org.junit.jupiter.api.Assertions.assertEquals("SHORTLISTED", applicationStatus(foreign));
    }

    @Test
    void recruiterListsOnlyTheirCompanyInterviews() throws Exception {
        World w = world();
        Company other = approvedRecruiterWithJob();
        mvc.perform(authedGet("/interviews", w.company().recruiter())).andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1))).andExpect(jsonPath("$.data[0].id").value(w.interviewId().toString()));
        mvc.perform(authedGet("/interviews", other.recruiter())).andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0))).andExpect(jsonPath("$.meta.page.totalElements").value(0));
    }

    // ------------------------------------------------------------------ seeker ownership (IDOR)

    @Test
    void anotherSeekerCannotReadOrAnswerTheInterview() throws Exception {
        World w = world();
        Seeker intruder = verifiedSeeker();
        mvc.perform(authedGet("/interviews/" + w.interviewId(), intruder.session())).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
        mvc.perform(postAs("/interviews/" + w.interviewId() + "/respond", RESPOND, intruder.session())).andExpect(status().isNotFound());
        mvc.perform(postAs("/interviews/" + w.interviewId() + "/respond", Map.of("response", "DECLINE"), intruder.session()))
                .andExpect(status().isNotFound());
        mvc.perform(authedGet("/interviews", intruder.session())).andExpect(status().isOk()).andExpect(jsonPath("$.data", hasSize(0)));
        mvc.perform(authedGet("/interviews/" + w.interviewId(), w.company().recruiter()))
                .andExpect(jsonPath("$.data.status").value("SCHEDULED")).andExpect(jsonPath("$.data.seekerResponse").value("PENDING"));
    }

    @Test
    void ownerSeesTheirInterviewWithoutRecruiterOnlyData() throws Exception {
        World w = world();
        mvc.perform(authedGet("/interviews/" + w.interviewId(), w.seeker().session())).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(w.interviewId().toString()))
                .andExpect(jsonPath("$.data.notes").doesNotExist())
                .andExpect(jsonPath("$.data.seeker").doesNotExist())
                .andExpect(jsonPath("$.data.scheduledBy.handle").exists())
                .andExpect(jsonPath("$.data.job.title").value("Backend Engineer"));
        mvc.perform(authedGet("/interviews", w.seeker().session())).andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1))).andExpect(jsonPath("$.data[0].notes").doesNotExist());
        mvc.perform(authedGet("/interviews/" + w.interviewId(), w.company().recruiter())).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.notes").value("Ask about the migration project"))
                .andExpect(jsonPath("$.data.seeker.id").value(w.seeker().userId().toString()));
    }

    @Test
    void applicationIdFilterNeverWidensTheScope() throws Exception {
        World w = world();
        Company other = approvedRecruiterWithJob();
        Seeker intruder = verifiedSeeker();
        mvc.perform(authedGet("/interviews?applicationId=" + w.applicationId(), other.recruiter())).andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
        mvc.perform(authedGet("/interviews?applicationId=" + w.applicationId(), intruder.session())).andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
        mvc.perform(authedGet("/interviews?applicationId=" + w.applicationId(), adminLogin())).andExpect(status().isForbidden());
    }

    @Test
    void unknownInterviewIdsLookLikeForeignOnes() throws Exception {
        World w = world();
        UUID missing = UUID.randomUUID();
        mvc.perform(authedGet("/interviews/" + missing, w.seeker().session())).andExpect(status().isNotFound());
        mvc.perform(authedGet("/interviews/" + missing, w.company().recruiter())).andExpect(status().isNotFound());
        mvc.perform(postAs("/interviews/" + missing + "/respond", RESPOND, w.seeker().session())).andExpect(status().isNotFound());
        mvc.perform(postAs("/interviews/" + missing + "/cancel", CANCEL, w.company().recruiter())).andExpect(status().isNotFound());
    }
}
