package com.jobforge.backend.interview.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jobforge.backend.shared.error.BusinessRuleException;
import com.jobforge.backend.shared.error.ValidationFailedException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Pure unit tests of the scheduling rules (no Spring, no database). */
class InterviewSchedulingTest {

    private static final Instant NOW = Instant.parse("2026-10-08T10:00:00Z");

    private static Interview interviewAt(Instant at, int minutes) {
        return new Interview(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), InterviewType.VIDEO, at, minutes,
                "Asia/Kolkata", "https://meet.example.test/x", InterviewStatus.SCHEDULED, InterviewResponse.PENDING, null,
                null, null, NOW, NOW);
    }

    @Test
    void timezonesMustBeIanaIds() {
        assertEquals("Asia/Kolkata", InterviewScheduling.requireTimezone("Asia/Kolkata"));
        assertEquals("UTC", InterviewScheduling.requireTimezone("UTC"));
        for (String bad : new String[] {"", "Mars/Olympus", "IST+5", "not a zone"}) {
            assertThrows(ValidationFailedException.class, () -> InterviewScheduling.requireTimezone(bad), bad);
        }
        assertThrows(ValidationFailedException.class, () -> InterviewScheduling.requireTimezone(null));
    }

    @Test
    void slotsMustBeInTheFutureButWithinAYear() {
        InterviewScheduling.requireBookable(NOW.plusSeconds(1), NOW);
        InterviewScheduling.requireBookable(NOW.plus(InterviewScheduling.MAX_ADVANCE), NOW);
        assertThrows(BusinessRuleException.class, () -> InterviewScheduling.requireBookable(NOW, NOW));
        assertThrows(BusinessRuleException.class, () -> InterviewScheduling.requireBookable(NOW.minusSeconds(60), NOW));
        assertThrows(BusinessRuleException.class,
                () -> InterviewScheduling.requireBookable(NOW.plus(InterviewScheduling.MAX_ADVANCE).plusSeconds(1), NOW));
    }

    @Test
    void overlapIsHalfOpen() {
        Instant start = NOW.plus(Duration.ofDays(1));
        Instant end = start.plus(Duration.ofMinutes(45));
        assertTrue(InterviewScheduling.overlaps(start, end, start.plus(Duration.ofMinutes(30)), end.plus(Duration.ofMinutes(30))));
        assertTrue(InterviewScheduling.overlaps(start, end, start, end));
        assertTrue(InterviewScheduling.overlaps(start, end, start.minusSeconds(600), start.plusSeconds(60)));
        assertFalse(InterviewScheduling.overlaps(start, end, end, end.plus(Duration.ofMinutes(30))), "back-to-back slots do not overlap");
        assertFalse(InterviewScheduling.overlaps(start, end, start.minus(Duration.ofMinutes(30)), start));
    }

    @Test
    void answersAreOnlyPossibleBeforeTheStartAndCompletionOnlyAfter() {
        Interview upcoming = interviewAt(NOW.plus(Duration.ofHours(2)), 30);
        Interview started = interviewAt(NOW.minus(Duration.ofHours(1)), 30);
        InterviewScheduling.requireNotStarted(upcoming, NOW);
        assertThrows(BusinessRuleException.class, () -> InterviewScheduling.requireNotStarted(started, NOW));
        InterviewScheduling.requireStarted(started, NOW);
        assertThrows(BusinessRuleException.class, () -> InterviewScheduling.requireStarted(upcoming, NOW));
    }

    @Test
    void reschedulingResetsTheSeekerResponse() {
        Interview confirmed = interviewAt(NOW.plus(Duration.ofDays(1)), 30)
                .responded(InterviewStatus.CONFIRMED, InterviewResponse.CONFIRMED, "ok");
        Interview moved = confirmed.rescheduled(InterviewType.PHONE, NOW.plus(Duration.ofDays(2)), 60, "UTC", "+91 00000", "notes");
        assertEquals(InterviewStatus.SCHEDULED, moved.status());
        assertEquals(InterviewResponse.PENDING, moved.seekerResponse());
        assertEquals(null, moved.seekerResponseNote());
        assertEquals(confirmed.id(), moved.id());
        assertEquals(moved.scheduledAt().plus(Duration.ofMinutes(60)), moved.endsAt());
    }
}
