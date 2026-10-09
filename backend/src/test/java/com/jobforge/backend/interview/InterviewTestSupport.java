package com.jobforge.backend.interview;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobforge.backend.support.ApiIntegrationTestSupport;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Fixtures for the interview ITs (Docker required). Applications are inserted directly: the apply flow needs a resume
 * upload and a complete profile, which is irrelevant for interview rules. Never run in the coding-only session.
 */
abstract class InterviewTestSupport extends ApiIntegrationTestSupport {

    /** An approved recruiter whose verified company owns one job. */
    protected record Company(Session recruiter, UUID recruiterUserId, String companyId, UUID jobId) {}

    protected record Seeker(Session session, UUID userId) {}

    protected Company approvedRecruiterWithJob() throws Exception {
        Account account = register("RECRUITER");
        Session recruiter = ready(account);
        String companyId = json.readTree(mvc.perform(authed(postJson("/companies", Map.of(
                        "name", "Interview Co " + unique(), "industry", "Software")), recruiter))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).at("/data/id").asText();
        Session admin = adminLogin();
        UUID userId = jdbc.queryForObject("SELECT id FROM core.users WHERE email = ?", UUID.class, account.email());
        mvc.perform(authed(postJson("/admin/recruiters/" + userId + "/approve", Map.of()), admin)).andExpect(status().isNoContent());
        mvc.perform(authed(postJson("/admin/companies/" + companyId + "/verify", Map.of()), admin)).andExpect(status().isNoContent());
        String created = mvc.perform(authed(postJson("/jobs", Map.of(
                        "title", "Backend Engineer",
                        "description", "Build and operate reliable services for our hiring platform, working with Java and PostgreSQL daily.",
                        "employmentType", "FULL_TIME", "workMode", "REMOTE", "experienceLevel", "MID")), recruiter))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID jobId = UUID.fromString(json.readTree(created).at("/data/id").asText());
        return new Company(recruiter, userId, companyId, jobId);
    }

    /** A recruiter who is registered and verified but NOT approved and has no company. */
    protected Session unapprovedRecruiter() throws Exception {
        return ready(register("RECRUITER"));
    }

    protected Seeker verifiedSeeker() throws Exception {
        Account account = register("JOB_SEEKER");
        Session session = ready(account);
        UUID userId = jdbc.queryForObject("SELECT id FROM core.users WHERE email = ?", UUID.class, account.email());
        return new Seeker(session, userId);
    }

    /** Inserts a resume and an application of {@code seeker} to {@code jobId} in the given status. */
    protected UUID application(Seeker seeker, UUID jobId, String status) {
        UUID profileId = jdbc.queryForObject("SELECT id FROM core.seeker_profiles WHERE user_id = ?", UUID.class, seeker.userId());
        UUID resumeId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO core.resumes (id, seeker_profile_id, original_filename, storage_key, content_type, size_bytes, sha256)
                VALUES (?, ?, 'cv.pdf', ?, 'application/pdf', 1000, ?)
                """, resumeId, profileId, "test/" + resumeId, "a".repeat(64));
        UUID applicationId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO core.applications (id, job_id, seeker_user_id, resume_id, status, profile_snapshot, applied_at, status_updated_at)
                VALUES (?, ?, ?, ?, ?, '{"headline":"Dev","skills":["java"],"yearsExperience":3}'::jsonb, now(), now())
                """, applicationId, jobId, seeker.userId(), resumeId, status);
        return applicationId;
    }

    protected static String inDays(int days) {
        return Instant.now().plus(days, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS).toString();
    }

    protected static Map<String, Object> scheduleBody(String scheduledAt) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "VIDEO");
        body.put("scheduledAt", scheduledAt);
        body.put("durationMinutes", 45);
        body.put("timezone", "Asia/Kolkata");
        body.put("locationOrLink", "https://meet.example.test/room-1");
        body.put("notes", "Ask about the migration project");
        return body;
    }

    /** Schedules an interview as the recruiter and returns its id. */
    protected UUID schedule(Session recruiter, UUID applicationId, String scheduledAt) throws Exception {
        String response = mvc.perform(authed(postJson("/applications/" + applicationId + "/interviews", scheduleBody(scheduledAt)), recruiter))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(response).at("/data/id").asText());
    }

    protected MockHttpServletRequestBuilder patchJson(String path, Object body, Session session) throws Exception {
        return authed(MockMvcRequestBuilders.patch(API + path).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)), session);
    }

    protected MockHttpServletRequestBuilder postAs(String path, Object body, Session session) throws Exception {
        return authed(postJson(path, body), session);
    }

    protected int outboxCount(UUID aggregateId, String eventType) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM platform.outbox_events WHERE aggregate_id = ? AND event_type = ?",
                Integer.class, aggregateId, eventType);
        return n == null ? 0 : n;
    }

    protected String applicationStatus(UUID applicationId) {
        return jdbc.queryForObject("SELECT status FROM core.applications WHERE id = ?", String.class, applicationId);
    }

    protected void startedAnHourAgo(UUID interviewId) {
        jdbc.update("UPDATE core.interviews SET scheduled_at = now() - interval '1 hour' WHERE id = ?", interviewId);
    }
}
