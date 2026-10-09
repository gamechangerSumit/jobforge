package com.jobforge.backend.interview.infra;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobforge.backend.interview.app.InterviewService;
import com.jobforge.backend.platform.events.EventEnvelope;
import com.jobforge.backend.platform.events.IdempotentEventConsumer;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Which application events close interviews (pure unit test, no Spring/Kafka). */
class InterviewApplicationEventsConsumerTest {

    private final InterviewService service = mock(InterviewService.class);
    private final InterviewApplicationEventsConsumer consumer =
            new InterviewApplicationEventsConsumer(mock(IdempotentEventConsumer.class), service, new ObjectMapper());
    private final UUID application = UUID.randomUUID();
    private final UUID job = UUID.randomUUID();
    private final UUID company = UUID.randomUUID();
    private final UUID seeker = UUID.randomUUID();

    private EventEnvelope event(String type, Map<String, Object> payload) {
        return new EventEnvelope(UUID.randomUUID(), type, 1, Instant.now(), "backend", null, "Application", application, null, payload);
    }

    @Test
    void withdrawalCancelsOpenInterviews() {
        consumer.handle(event("ApplicationWithdrawn", Map.of("applicationId", application.toString(), "jobId", job.toString(),
                "companyId", company.toString(), "seekerUserId", seeker.toString())));
        verify(service).cancelOpenInterviewsOfClosedApplication(application, job, company, seeker, "APPLICATION_WITHDRAWN");
    }

    @Test
    void rejectionCancelsOpenInterviews() {
        consumer.handle(event("ApplicationStatusChanged", Map.of("applicationId", application.toString(), "jobId", job.toString(),
                "companyId", company.toString(), "seekerUserId", seeker.toString(), "from", "INTERVIEW", "to", "REJECTED")));
        verify(service).cancelOpenInterviewsOfClosedApplication(application, job, company, seeker, "APPLICATION_REJECTED");
    }

    @Test
    void otherStatusChangesAndEventsAreIgnored() {
        for (String to : new String[] {"UNDER_REVIEW", "SHORTLISTED", "INTERVIEW", "OFFERED", "HIRED"}) {
            consumer.handle(event("ApplicationStatusChanged", Map.of("applicationId", application.toString(), "jobId", job.toString(),
                    "from", "SUBMITTED", "to", to)));
        }
        consumer.handle(event("ApplicationSubmitted", Map.of("applicationId", application.toString(), "jobId", job.toString())));
        verify(service, never()).cancelOpenInterviewsOfClosedApplication(any(), any(), any(), any(), anyString());
    }

    @Test
    void malformedPayloadsAreSkippedNotRetried() {
        consumer.handle(event("ApplicationWithdrawn", Map.of("seekerUserId", seeker.toString())));
        verify(service, never()).cancelOpenInterviewsOfClosedApplication(any(), any(), any(), any(), anyString());
    }
}
