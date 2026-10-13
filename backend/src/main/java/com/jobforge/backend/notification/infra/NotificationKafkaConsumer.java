package com.jobforge.backend.notification.infra;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobforge.backend.notification.app.NotificationService;
import com.jobforge.backend.platform.events.EventEnvelope;
import com.jobforge.backend.platform.events.IdempotentEventConsumer;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
public class NotificationKafkaConsumer {

    private static final String GROUP =
            "jobforge-notification";

    private final IdempotentEventConsumer consumer;
    private final NotificationService notifications;
    private final ObjectMapper mapper;

    public NotificationKafkaConsumer(
            IdempotentEventConsumer consumer,
            NotificationService notifications,
            ObjectMapper mapper
    ) {
        this.consumer = consumer;
        this.notifications = notifications;
        this.mapper = mapper;
    }

    @KafkaListener(
            topics = {
                    "jobforge.users.v1",
                    "jobforge.jobs.v1",
                    "jobforge.applications.v1",
                    "jobforge.interviews.v1",
                    "jobforge.community.v1",
                    "jobforge.moderation.v1",
                    "jobforge.ai.v1"
            },
            groupId = GROUP
    )
    public void consume(
            String rawMessage
    ) {

        consumer.consume(
                GROUP,
                rawMessage,
                this::handle
        );
    }

    private void handle(
            EventEnvelope event
    ) {

        switch (event.eventType()) {

            case "ApplicationSubmitted" ->
                    applicationSubmitted(event);

            case "ApplicationStatusChanged" ->
                    applicationStatusChanged(event);

            case "InterviewScheduled" ->
                    interviewScheduled(event);

            case "InterviewUpdated" ->
                    interviewUpdated(event);

            case "InterviewCancelled" ->
                    interviewCancelled(event);

            case "InterviewResponded" ->
                    interviewResponded(event);

            case "AiRequestCompleted" ->
                    aiCompleted(event);
            case "RecruiterApproved" ->
                    moderationOutcome(event, "userId", "RECRUITER_APPROVED", "Recruiter account approved",
                            "Your recruiter account was approved. You can now publish jobs once your company is verified.");
            case "RecruiterRejected" ->
                    moderationOutcome(event, "userId", "RECRUITER_REJECTED", "Recruiter account not approved",
                            "Your recruiter account was not approved. Contact support for details.");
            case "CompanyVerified" ->
                    moderationOutcome(event, "ownerId", "COMPANY_VERIFIED", "Company verified",
                            "Your company was verified. Approved recruiters can now publish jobs.");
            case "ContentModerated" ->
                    moderationOutcome(event, "ownerUserId", "CONTENT_MODERATED", "Moderation notice",
                            "A moderator took action on content or an account linked to you after a report. "
                                    + "Please review the community guidelines.");
            case "CompanyRejected" ->
                    moderationOutcome(event, "ownerId", "COMPANY_REJECTED", "Company verification rejected",
                            "Your company verification was rejected. Review the company page for the reason.");

            default -> {
                // Event is valid but does not require
                // a notification for the current consumer.
            }
        }
    }

    private void applicationSubmitted(
            EventEnvelope event
    ) {

        JsonNode payload =
                mapper.valueToTree(event.payload());

        UUID recipient =
                UUID.fromString(
                        firstText(payload, "seekerUserId", "seekerId")
                );

        notifications.create(
                recipient,
                "APPLICATION_SUBMITTED",
                "Application submitted",
                "Your application was submitted successfully.",
                Map.of(
                        "jobId",
                        payload.path("jobId").asText()
                ),
                "APP_SUBMITTED:"
                        + event.aggregateId()
        );
    }

    private void applicationStatusChanged(
            EventEnvelope event
    ) {

        JsonNode payload =
                mapper.valueToTree(event.payload());

        UUID recipient =
                UUID.fromString(
                        firstText(payload, "seekerUserId", "seekerId")
                );

        String status = firstText(payload, "to", "status");

        notifications.create(
                recipient,
                "APPLICATION_STATUS_CHANGED",
                "Application status updated",
                "Your application status changed to "
                        + status + ".",
                Map.of(
                        "applicationId",
                        event.aggregateId().toString(),
                        "status",
                        status
                ),
                "APP_STATUS:"
                        + event.aggregateId()
                        + ":"
                        + status
        );
    }

    private void interviewScheduled(
            EventEnvelope event
    ) {

        JsonNode payload = mapper.valueToTree(event.payload());

        notifyInterview(
                payload,
                firstText(payload, "seekerUserId", "seekerId"),
                "INTERVIEW_SCHEDULED",
                "Interview scheduled",
                "You have an interview" + forJob(payload) + " on " + when(payload) + ".",
                "INTERVIEW_SCHEDULED:" + event.aggregateId()
        );
    }

    private void interviewUpdated(
            EventEnvelope event
    ) {

        JsonNode payload = mapper.valueToTree(event.payload());

        // Every reschedule notifies again, so the dedupe key carries the event id (redelivery of the same event is
        // already ignored by the idempotent consumer).
        notifyInterview(
                payload,
                firstText(payload, "seekerUserId", "seekerId"),
                "INTERVIEW_UPDATED",
                "Interview updated",
                "Your interview" + forJob(payload) + " was updated. It is now on " + when(payload)
                        + ". Please confirm or decline the new details.",
                "INTERVIEW_UPDATED:" + event.aggregateId() + ":" + event.eventId()
        );
    }

    private void interviewCancelled(
            EventEnvelope event
    ) {

        JsonNode payload = mapper.valueToTree(event.payload());
        String cause = payload.path("cause").asText("RECRUITER");

        if ("APPLICATION_WITHDRAWN".equals(cause)) {
            // The seeker withdrew: tell the recruiter who scheduled the interview.
            notifyInterview(
                    payload,
                    firstText(payload, "recruiterUserId", "scheduledBy"),
                    "INTERVIEW_CANCELLED",
                    "Interview cancelled",
                    "The interview" + forJob(payload) + " on " + when(payload) + " was cancelled because the candidate withdrew.",
                    "INTERVIEW_CANCELLED:" + event.aggregateId()
            );
            return;
        }
        if ("APPLICATION_REJECTED".equals(cause)) {
            return; // the recruiter rejected the application and the seeker is told by the status notification
        }

        notifyInterview(
                payload,
                firstText(payload, "seekerUserId", "seekerId"),
                "INTERVIEW_CANCELLED",
                "Interview cancelled",
                "Your interview" + forJob(payload) + " on " + when(payload) + " was cancelled.",
                "INTERVIEW_CANCELLED:" + event.aggregateId()
        );
    }

    /** The seeker answered: the recruiter who scheduled the interview is told. */
    private void interviewResponded(
            EventEnvelope event
    ) {

        JsonNode payload = mapper.valueToTree(event.payload());
        boolean confirmed = "CONFIRMED".equals(payload.path("response").asText());

        notifyInterview(
                payload,
                firstText(payload, "recruiterUserId", "scheduledBy"),
                "INTERVIEW_RESPONSE",
                confirmed ? "Interview confirmed" : "Interview declined",
                "The candidate " + (confirmed ? "confirmed" : "declined") + " the interview" + forJob(payload) + " on "
                        + when(payload) + ".",
                "INTERVIEW_RESPONSE:" + event.aggregateId() + ":" + event.eventId()
        );
    }

    private void notifyInterview(
            JsonNode payload,
            String recipient,
            String type,
            String title,
            String body,
            String dedupeKey
    ) {

        if (recipient.isBlank()) {
            return;
        }

        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("interviewId", payload.path("interviewId").asText());
        data.put("applicationId", payload.path("applicationId").asText());
        data.put("jobId", payload.path("jobId").asText());

        notifications.create(
                UUID.fromString(recipient),
                type,
                title,
                body,
                data,
                dedupeKey
        );
    }

    private static String forJob(JsonNode payload) {
        String title = payload.path("jobTitle").asText("");
        return title.isBlank() || "null".equals(title) ? "" : " for " + title;
    }

    private static final java.time.format.DateTimeFormatter WHEN =
            java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy 'at' HH:mm 'UTC'", java.util.Locale.ENGLISH)
                    .withZone(java.time.ZoneOffset.UTC);

    private static String when(JsonNode payload) {
        String raw = payload.path("scheduledAt").asText("");
        try {
            return WHEN.format(java.time.Instant.parse(raw));
        } catch (java.time.format.DateTimeParseException e) {
            return "the scheduled time";
        }
    }

    /** First non-blank string among the given fields (producers and older consumers used different names). */
    private static String firstText(JsonNode payload, String... fields) {
        for (String field : fields) {
            String value = payload.path(field).asText("");
            if (!value.isBlank() && !"null".equals(value)) {
                return value;
            }
        }
        return "";
    }

    private void aiCompleted(
            EventEnvelope event
    ) {

        JsonNode payload =
                mapper.valueToTree(event.payload());

        UUID recipient =
                event.actor() == null
                        ? null
                        : event.actor().userId();

        if (recipient == null) {
            return;
        }

        notifications.create(
                recipient,
                "SYSTEM_ANNOUNCEMENT",
                "AI request completed",
                "Your AI request has completed.",
                Map.of(
                        "requestId",
                        event.aggregateId().toString()
                ),
                "AI_COMPLETED:"
                        + event.aggregateId()
        );
    }

    private void moderationOutcome(EventEnvelope event, String recipientField, String type, String title, String body) {
        JsonNode payload = mapper.valueToTree(event.payload());
        String raw = payload.path(recipientField).asText("");
        if (raw.isBlank() || "null".equals(raw)) {
            return;
        }
        notifications.create(UUID.fromString(raw), type, title, body,
                Map.of("entityId", event.aggregateId().toString()), type + ":" + event.aggregateId() + ":" + event.eventId());
    }
}
