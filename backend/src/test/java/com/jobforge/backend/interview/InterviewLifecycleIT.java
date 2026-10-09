package com.jobforge.backend.interview;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobforge.backend.interview.app.InterviewService;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Scheduling, application-status integration, reschedule/cancel/respond/complete transitions, invalid transitions,
 * events and audit. Requires Docker (not executed in session 1).
 */
class InterviewLifecycleIT extends InterviewTestSupport {

    @Autowired InterviewService interviewService;

    private record World(Company company, Seeker seeker, UUID applicationId) {
        Session recruiter() {
            return company.recruiter();
        }

        Session seekerSession() {
            return seeker.session();
        }
    }

    private World world(String applicationStatus) throws Exception {
        Company company = approvedRecruiterWithJob();
        Seeker seeker = verifiedSeeker();
        return new World(company, seeker, application(seeker, company.jobId(), applicationStatus));
    }

    // ------------------------------------------------------------------ schedule

    @Test
    void schedulingMovesAShortlistedApplicationToInterviewAndEmitsEvents() throws Exception {
        World w = world("SHORTLISTED");
        int auditBefore = auditCount("INTERVIEW_SCHEDULED");

        String body = mvc.perform(authed(postJson("/applications/" + w.applicationId() + "/interviews", scheduleBody(inDays(3))), w.recruiter()))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.data.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.data.seekerResponse").value("PENDING"))
                .andExpect(jsonPath("$.data.applicationStatus").value("INTERVIEW"))
                .andExpect(jsonPath("$.data.type").value("VIDEO"))
                .andExpect(jsonPath("$.data.timezone").value("Asia/Kolkata"))
                .andExpect(jsonPath("$.data.notes").value("Ask about the migration project"))
                .andReturn().getResponse().getContentAsString();
        UUID interviewId = UUID.fromString(json.readTree(body).at("/data/id").asText());

        Assertions.assertEquals("INTERVIEW", applicationStatus(w.applicationId()));
        Integer history = jdbc.queryForObject(
                "SELECT count(*) FROM core.application_status_history WHERE application_id = ? AND from_status = 'SHORTLISTED' AND to_status = 'INTERVIEW'",
                Integer.class, w.applicationId());
        Assertions.assertEquals(1, history);
        Assertions.assertEquals(1, outboxCount(interviewId, "InterviewScheduled"));
        Assertions.assertEquals(1, outboxCount(w.applicationId(), "ApplicationStatusChanged"));
        Assertions.assertEquals(auditBefore + 1, auditCount("INTERVIEW_SCHEDULED"));
    }

    @Test
    void eventPayloadsCarryNoFreeText() throws Exception {
        World w = world("SHORTLISTED");
        UUID interviewId = schedule(w.recruiter(), w.applicationId(), inDays(3));
        String payload = jdbc.queryForObject("SELECT payload::text FROM platform.outbox_events WHERE aggregate_id = ? AND event_type = 'InterviewScheduled'",
                String.class, interviewId);
        Assertions.assertFalse(payload.contains("migration project"), "recruiter notes must not travel in events");
        Assertions.assertFalse(payload.contains("meet.example.test"), "meeting links must not travel in events");
        Assertions.assertTrue(payload.contains(w.seeker().userId().toString()));
    }

    @Test
    void schedulingIsRefusedUnlessTheApplicationIsShortlistedOrAlreadyInInterview() throws Exception {
        for (String blocked : new String[] {"SUBMITTED", "UNDER_REVIEW", "OFFERED", "HIRED", "REJECTED", "WITHDRAWN"}) {
            World w = world(blocked);
            mvc.perform(authed(postJson("/applications/" + w.applicationId() + "/interviews", scheduleBody(inDays(3))), w.recruiter()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("INVALID_STATE_TRANSITION"));
            Assertions.assertEquals(blocked, applicationStatus(w.applicationId()), "application must be untouched for " + blocked);
            Integer interviews = jdbc.queryForObject("SELECT count(*) FROM core.interviews WHERE application_id = ?", Integer.class, w.applicationId());
            Assertions.assertEquals(0, interviews);
        }
    }

    @Test
    void aSecondRoundKeepsTheApplicationInInterview() throws Exception {
        World w = world("INTERVIEW");
        schedule(w.recruiter(), w.applicationId(), inDays(3));
        schedule(w.recruiter(), w.applicationId(), inDays(10));
        Assertions.assertEquals("INTERVIEW", applicationStatus(w.applicationId()));
        mvc.perform(authedGet("/interviews", w.recruiter())).andExpect(jsonPath("$.data", hasSize(2)));
    }

    @Test
    void overlappingActiveInterviewsOfOneApplicationConflict() throws Exception {
        World w = world("SHORTLISTED");
        String slot = inDays(4);
        schedule(w.recruiter(), w.applicationId(), slot);
        mvc.perform(authed(postJson("/applications/" + w.applicationId() + "/interviews", scheduleBody(slot)), w.recruiter()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("CONFLICT"));
    }

    @Test
    void schedulingValidatesTheRequest() throws Exception {
        World w = world("SHORTLISTED");
        String path = "/applications/" + w.applicationId() + "/interviews";
        // 422: valid syntax, breaks a scheduling rule
        mvc.perform(authed(postJson(path, scheduleBody(inDays(-1))), w.recruiter()))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error.code").value("BUSINESS_RULE_VIOLATED"));
        mvc.perform(authed(postJson(path, scheduleBody(inDays(800))), w.recruiter())).andExpect(status().isUnprocessableEntity());
        // 400: field validation
        Map<String, Object> shortSlot = new HashMap<>(scheduleBody(inDays(3)));
        shortSlot.put("durationMinutes", 10);
        mvc.perform(authed(postJson(path, shortSlot), w.recruiter())).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        Map<String, Object> badZone = new HashMap<>(scheduleBody(inDays(3)));
        badZone.put("timezone", "Mars/Olympus");
        mvc.perform(authed(postJson(path, badZone), w.recruiter())).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        Map<String, Object> scriptLink = new HashMap<>(scheduleBody(inDays(3)));
        scriptLink.put("locationOrLink", "javascript:alert(1)");
        mvc.perform(authed(postJson(path, scriptLink), w.recruiter())).andExpect(status().isBadRequest());
        Map<String, Object> missing = new HashMap<>(scheduleBody(inDays(3)));
        missing.remove("locationOrLink");
        mvc.perform(authed(postJson(path, missing), w.recruiter())).andExpect(status().isBadRequest());
        Map<String, Object> unknown = new HashMap<>(scheduleBody(inDays(3)));
        unknown.put("seekerResponse", "CONFIRMED");
        mvc.perform(authed(postJson(path, unknown), w.recruiter())).andExpect(status().isBadRequest());
        Integer interviews = jdbc.queryForObject("SELECT count(*) FROM core.interviews WHERE application_id = ?", Integer.class, w.applicationId());
        Assertions.assertEquals(0, interviews, "no rejected request may leave an interview or an application move behind");
        Assertions.assertEquals("SHORTLISTED", applicationStatus(w.applicationId()));
    }

    // ------------------------------------------------------------------ seeker response

    @Test
    void seekerConfirmsAndTheRecruiterIsNotified() throws Exception {
        World w = world("SHORTLISTED");
        UUID id = schedule(w.recruiter(), w.applicationId(), inDays(3));
        mvc.perform(postAs("/interviews/" + id + "/respond", Map.of("response", "CONFIRM", "note", "See you then"), w.seekerSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.seekerResponse").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.seekerResponseNote").value("See you then"));
        Assertions.assertEquals(1, outboxCount(id, "InterviewResponded"));
        // an identical second answer is an idempotent replay: no new event
        mvc.perform(postAs("/interviews/" + id + "/respond", Map.of("response", "CONFIRM"), w.seekerSession()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CONFIRMED"));
        Assertions.assertEquals(1, outboxCount(id, "InterviewResponded"));
    }

    @Test
    void seekerCanChangeTheirMindWhileTheInterviewIsUpcoming() throws Exception {
        World w = world("SHORTLISTED");
        UUID id = schedule(w.recruiter(), w.applicationId(), inDays(3));
        mvc.perform(postAs("/interviews/" + id + "/respond", Map.of("response", "CONFIRM"), w.seekerSession())).andExpect(status().isOk());
        mvc.perform(postAs("/interviews/" + id + "/respond", Map.of("response", "DECLINE"), w.seekerSession()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("DECLINED"))
                .andExpect(jsonPath("$.data.seekerResponse").value("DECLINED"));
        // a declined interview cannot simply be confirmed again: the recruiter has to reschedule
        mvc.perform(postAs("/interviews/" + id + "/respond", Map.of("response", "CONFIRM"), w.seekerSession()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("INVALID_STATE_TRANSITION"));
    }

    @Test
    void respondValidatesTheBody() throws Exception {
        World w = world("SHORTLISTED");
        UUID id = schedule(w.recruiter(), w.applicationId(), inDays(3));
        mvc.perform(postAs("/interviews/" + id + "/respond", Map.of("response", "MAYBE"), w.seekerSession())).andExpect(status().isBadRequest());
        mvc.perform(postAs("/interviews/" + id + "/respond", Map.of(), w.seekerSession())).andExpect(status().isBadRequest());
        mvc.perform(postAs("/interviews/" + id + "/respond", Map.of("response", "CONFIRM", "status", "COMPLETED"), w.seekerSession()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void seekerCannotAnswerOnceTheInterviewHasStarted() throws Exception {
        World w = world("SHORTLISTED");
        UUID id = schedule(w.recruiter(), w.applicationId(), inDays(3));
        startedAnHourAgo(id);
        mvc.perform(postAs("/interviews/" + id + "/respond", Map.of("response", "CONFIRM"), w.seekerSession()))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error.code").value("BUSINESS_RULE_VIOLATED"));
    }

    // ------------------------------------------------------------------ reschedule / edit

    @Test
    void reschedulingResetsConfirmationAndNotifiesTheSeeker() throws Exception {
        World w = world("SHORTLISTED");
        UUID id = schedule(w.recruiter(), w.applicationId(), inDays(3));
        mvc.perform(postAs("/interviews/" + id + "/respond", Map.of("response", "CONFIRM"), w.seekerSession())).andExpect(status().isOk());

        mvc.perform(patchJson("/interviews/" + id, Map.of("scheduledAt", inDays(5), "locationOrLink", "Room 4, HQ"), w.recruiter()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.data.seekerResponse").value("PENDING"))
                .andExpect(jsonPath("$.data.seekerResponseNote").doesNotExist())
                .andExpect(jsonPath("$.data.locationOrLink").value("Room 4, HQ"));
        Assertions.assertEquals(1, outboxCount(id, "InterviewUpdated"));
    }

    @Test
    void aDeclinedInterviewCanBeRescheduledBackToScheduled() throws Exception {
        World w = world("SHORTLISTED");
        UUID id = schedule(w.recruiter(), w.applicationId(), inDays(3));
        mvc.perform(postAs("/interviews/" + id + "/respond", Map.of("response", "DECLINE"), w.seekerSession())).andExpect(status().isOk());
        mvc.perform(patchJson("/interviews/" + id, Map.of("scheduledAt", inDays(6)), w.recruiter()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.data.seekerResponse").value("PENDING"));
    }

    @Test
    void editingOnlyInternalNotesNeitherResetsNorNotifies() throws Exception {
        World w = world("SHORTLISTED");
        UUID id = schedule(w.recruiter(), w.applicationId(), inDays(3));
        mvc.perform(postAs("/interviews/" + id + "/respond", Map.of("response", "CONFIRM"), w.seekerSession())).andExpect(status().isOk());
        mvc.perform(patchJson("/interviews/" + id, Map.of("notes", "Panel: Priya and Sam"), w.recruiter()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.notes").value("Panel: Priya and Sam"));
        Assertions.assertEquals(0, outboxCount(id, "InterviewUpdated"));
        mvc.perform(patchJson("/interviews/" + id, Map.of("notes", ""), w.recruiter()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.notes").doesNotExist());
    }

    @Test
    void reschedulingValidatesTheNewSlot() throws Exception {
        World w = world("SHORTLISTED");
        UUID first = schedule(w.recruiter(), w.applicationId(), inDays(3));
        UUID second = schedule(w.recruiter(), w.applicationId(), inDays(8));
        mvc.perform(patchJson("/interviews/" + second, Map.of("scheduledAt", inDays(-2)), w.recruiter())).andExpect(status().isUnprocessableEntity());
        mvc.perform(patchJson("/interviews/" + second, Map.of("scheduledAt", inDays(3)), w.recruiter()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("CONFLICT"));
        mvc.perform(patchJson("/interviews/" + second, Map.of("timezone", "Nowhere/City"), w.recruiter())).andExpect(status().isBadRequest());
        mvc.perform(patchJson("/interviews/" + second, Map.of("status", "COMPLETED"), w.recruiter())).andExpect(status().isBadRequest());
        // moving an interview onto its own slot is fine
        mvc.perform(patchJson("/interviews/" + first, Map.of("durationMinutes", 60), w.recruiter())).andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ cancel and invalid transitions

    @Test
    void cancellingRecordsTheReasonAndBlocksEveryFurtherChange() throws Exception {
        World w = world("SHORTLISTED");
        UUID id = schedule(w.recruiter(), w.applicationId(), inDays(3));
        int auditBefore = auditCount("INTERVIEW_CANCELLED");

        mvc.perform(postAs("/interviews/" + id + "/cancel", Map.of("reason", "Position filled"), w.recruiter()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.cancelledReason").value("Position filled"));
        Assertions.assertEquals(1, outboxCount(id, "InterviewCancelled"));
        Assertions.assertEquals(auditBefore + 1, auditCount("INTERVIEW_CANCELLED"));
        mvc.perform(authedGet("/interviews/" + id, w.seekerSession())).andExpect(jsonPath("$.data.cancelledReason").value("Position filled"));

        // every further transition is invalid
        mvc.perform(postAs("/interviews/" + id + "/cancel", Map.of("reason", "again"), w.recruiter()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("INVALID_STATE_TRANSITION"));
        mvc.perform(patchJson("/interviews/" + id, Map.of("scheduledAt", inDays(9)), w.recruiter()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("INVALID_STATE_TRANSITION"));
        mvc.perform(postAs("/interviews/" + id + "/respond", Map.of("response", "CONFIRM"), w.seekerSession()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("INVALID_STATE_TRANSITION"));
        mvc.perform(postAs("/interviews/" + id + "/complete", Map.of("outcome", "COMPLETED"), w.recruiter()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("INVALID_STATE_TRANSITION"));
        // the freed slot can be booked again
        schedule(w.recruiter(), w.applicationId(), inDays(3));
    }

    @Test
    void cancelRequiresAReason() throws Exception {
        World w = world("SHORTLISTED");
        UUID id = schedule(w.recruiter(), w.applicationId(), inDays(3));
        mvc.perform(postAs("/interviews/" + id + "/cancel", Map.of(), w.recruiter())).andExpect(status().isBadRequest());
        mvc.perform(postAs("/interviews/" + id + "/cancel", Map.of("reason", "   "), w.recruiter())).andExpect(status().isBadRequest());
    }

    @Test
    void aClosedApplicationBlocksSeekerAnswersButNotCancellation() throws Exception {
        World w = world("SHORTLISTED");
        UUID id = schedule(w.recruiter(), w.applicationId(), inDays(3));
        jdbc.update("UPDATE core.applications SET status = 'WITHDRAWN' WHERE id = ?", w.applicationId());
        mvc.perform(postAs("/interviews/" + id + "/respond", Map.of("response", "CONFIRM"), w.seekerSession()))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(patchJson("/interviews/" + id, Map.of("scheduledAt", inDays(6)), w.recruiter())).andExpect(status().isUnprocessableEntity());
        mvc.perform(postAs("/interviews/" + id + "/cancel", Map.of("reason", "Candidate withdrew"), w.recruiter()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    // ------------------------------------------------------------------ complete / no-show

    @Test
    void completionIsRefusedBeforeTheInterviewStarts() throws Exception {
        World w = world("SHORTLISTED");
        UUID id = schedule(w.recruiter(), w.applicationId(), inDays(3));
        mvc.perform(postAs("/interviews/" + id + "/complete", Map.of("outcome", "COMPLETED"), w.recruiter()))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error.code").value("BUSINESS_RULE_VIOLATED"));
        mvc.perform(postAs("/interviews/" + id + "/complete", Map.of("outcome", "NO_SHOW"), w.recruiter())).andExpect(status().isUnprocessableEntity());
        mvc.perform(postAs("/interviews/" + id + "/complete", Map.of("outcome", "CANCELLED"), w.recruiter())).andExpect(status().isBadRequest());
    }

    @Test
    void completedAndNoShowInterviewsAreFinal() throws Exception {
        World w = world("SHORTLISTED");
        UUID done = schedule(w.recruiter(), w.applicationId(), inDays(3));
        UUID missed = schedule(w.recruiter(), w.applicationId(), inDays(9));
        startedAnHourAgo(done);
        mvc.perform(postAs("/interviews/" + done + "/complete", Map.of("outcome", "COMPLETED"), w.recruiter()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("COMPLETED"));
        startedAnHourAgo(missed);
        mvc.perform(postAs("/interviews/" + missed + "/complete", Map.of("outcome", "NO_SHOW"), w.recruiter()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("NO_SHOW"));
        for (UUID id : new UUID[] {done, missed}) {
            mvc.perform(postAs("/interviews/" + id + "/cancel", Map.of("reason", "late"), w.recruiter())).andExpect(status().isConflict());
            mvc.perform(patchJson("/interviews/" + id, Map.of("durationMinutes", 30), w.recruiter())).andExpect(status().isConflict());
            mvc.perform(postAs("/interviews/" + id + "/respond", Map.of("response", "CONFIRM"), w.seekerSession())).andExpect(status().isConflict());
            mvc.perform(postAs("/interviews/" + id + "/complete", Map.of("outcome", "COMPLETED"), w.recruiter())).andExpect(status().isConflict());
        }
    }

    @Test
    void aDeclinedInterviewCannotBeCompleted() throws Exception {
        World w = world("SHORTLISTED");
        UUID id = schedule(w.recruiter(), w.applicationId(), inDays(3));
        mvc.perform(postAs("/interviews/" + id + "/respond", Map.of("response", "DECLINE"), w.seekerSession())).andExpect(status().isOk());
        startedAnHourAgo(id);
        mvc.perform(postAs("/interviews/" + id + "/complete", Map.of("outcome", "COMPLETED"), w.recruiter()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("INVALID_STATE_TRANSITION"));
    }

    // ------------------------------------------------------------------ list filters

    @Test
    void listSupportsStatusAndRangeFiltersAndRejectsUnknownParameters() throws Exception {
        World w = world("INTERVIEW");
        UUID soon = schedule(w.recruiter(), w.applicationId(), inDays(2));
        UUID later = schedule(w.recruiter(), w.applicationId(), inDays(20));
        mvc.perform(postAs("/interviews/" + later + "/cancel", Map.of("reason", "Rescoped"), w.recruiter())).andExpect(status().isOk());

        mvc.perform(authedGet("/interviews?status=CANCELLED", w.recruiter())).andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(later.toString()));
        mvc.perform(authedGet("/interviews?to=" + inDays(10), w.recruiter())).andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(soon.toString()));
        mvc.perform(authedGet("/interviews?from=" + inDays(10), w.seekerSession())).andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(later.toString()));
        mvc.perform(authedGet("/interviews", w.recruiter())).andExpect(jsonPath("$.data[0].id").value(soon.toString()))
                .andExpect(jsonPath("$.meta.page.totalElements").value(2));
        mvc.perform(authedGet("/interviews?status=BOGUS", w.recruiter())).andExpect(status().isBadRequest());
        mvc.perform(authedGet("/interviews?from=yesterday", w.recruiter())).andExpect(status().isBadRequest());
        mvc.perform(authedGet("/interviews?from=" + inDays(9) + "&to=" + inDays(1), w.recruiter())).andExpect(status().isBadRequest());
        mvc.perform(authedGet("/interviews?foo=bar", w.recruiter())).andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ applicationId filter

    @Test
    void listCanBeNarrowedToOneApplication() throws Exception {
        World a = world("SHORTLISTED");
        Seeker other = verifiedSeeker();
        UUID otherApplication = application(other, a.company().jobId(), "SHORTLISTED");
        UUID mine = schedule(a.recruiter(), a.applicationId(), inDays(3));
        schedule(a.recruiter(), otherApplication, inDays(4));

        mvc.perform(authedGet("/interviews?applicationId=" + a.applicationId(), a.recruiter())).andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(mine.toString()));
        mvc.perform(authedGet("/interviews?applicationId=" + a.applicationId(), a.seekerSession())).andExpect(jsonPath("$.data", hasSize(1)));
        // out-of-scope applications yield an empty page, never someone else's data
        mvc.perform(authedGet("/interviews?applicationId=" + otherApplication, a.seekerSession())).andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
        Session outsider = approvedRecruiterWithJob().recruiter();
        mvc.perform(authedGet("/interviews?applicationId=" + a.applicationId(), outsider)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
        mvc.perform(authedGet("/interviews?applicationId=not-a-uuid", a.recruiter())).andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ application closed (system cancellation)

    @Test
    void closingTheApplicationCancelsOpenInterviewsOnly() throws Exception {
        World w = world("INTERVIEW");
        UUID open = schedule(w.recruiter(), w.applicationId(), inDays(3));
        UUID declined = schedule(w.recruiter(), w.applicationId(), inDays(6));
        mvc.perform(postAs("/interviews/" + declined + "/respond", Map.of("response", "DECLINE"), w.seekerSession())).andExpect(status().isOk());
        UUID done = schedule(w.recruiter(), w.applicationId(), inDays(9));
        startedAnHourAgo(done);
        mvc.perform(postAs("/interviews/" + done + "/complete", Map.of("outcome", "COMPLETED"), w.recruiter())).andExpect(status().isOk());

        UUID jobId = w.company().jobId();
        int cancelled = interviewService.cancelOpenInterviewsOfClosedApplication(w.applicationId(), jobId,
                UUID.fromString(w.company().companyId()), w.seeker().userId(), "APPLICATION_WITHDRAWN");

        Assertions.assertEquals(2, cancelled);
        mvc.perform(authedGet("/interviews/" + open, w.seekerSession())).andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.cancelledReason").value("The candidate withdrew the application."));
        mvc.perform(authedGet("/interviews/" + declined, w.recruiter())).andExpect(jsonPath("$.data.status").value("CANCELLED"));
        mvc.perform(authedGet("/interviews/" + done, w.recruiter())).andExpect(jsonPath("$.data.status").value("COMPLETED"));
        Assertions.assertEquals(1, outboxCount(open, "InterviewCancelled"));
        String payload = jdbc.queryForObject("SELECT payload::text FROM platform.outbox_events WHERE aggregate_id = ? AND event_type = 'InterviewCancelled'",
                String.class, open);
        Assertions.assertTrue(payload.contains("APPLICATION_WITHDRAWN"));

        // idempotent: a second delivery finds nothing open and emits nothing
        Assertions.assertEquals(0, interviewService.cancelOpenInterviewsOfClosedApplication(w.applicationId(), jobId,
                UUID.fromString(w.company().companyId()), w.seeker().userId(), "APPLICATION_WITHDRAWN"));
        Assertions.assertEquals(1, outboxCount(open, "InterviewCancelled"));
    }
}
