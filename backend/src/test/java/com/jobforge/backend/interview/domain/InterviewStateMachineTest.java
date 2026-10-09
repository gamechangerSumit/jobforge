package com.jobforge.backend.interview.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jobforge.backend.shared.error.ConflictException;
import com.jobforge.backend.shared.error.ErrorCode;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Pure unit tests of the interview transition table (no Spring, no database). */
class InterviewStateMachineTest {

    private static final Set<InterviewStatus> OPEN = EnumSet.of(InterviewStatus.SCHEDULED, InterviewStatus.CONFIRMED, InterviewStatus.DECLINED);

    @ParameterizedTest
    @EnumSource(InterviewStatus.class)
    void editAndCancelAreAllowedExactlyFromOpenStatuses(InterviewStatus status) {
        assertEquals(OPEN.contains(status), InterviewStateMachine.canEdit(status));
        assertEquals(OPEN.contains(status), InterviewStateMachine.canCancel(status));
        if (OPEN.contains(status)) {
            InterviewStateMachine.requireEditable(status);
            InterviewStateMachine.requireCancellable(status);
        } else {
            assertInvalid(() -> InterviewStateMachine.requireEditable(status));
            assertInvalid(() -> InterviewStateMachine.requireCancellable(status));
        }
    }

    @Test
    void seekerAnswersMapToStatuses() {
        assertEquals(InterviewStatus.CONFIRMED, InterviewStateMachine.afterResponse(InterviewStatus.SCHEDULED, SeekerChoice.CONFIRM));
        assertEquals(InterviewStatus.DECLINED, InterviewStateMachine.afterResponse(InterviewStatus.SCHEDULED, SeekerChoice.DECLINE));
        assertEquals(InterviewStatus.DECLINED, InterviewStateMachine.afterResponse(InterviewStatus.CONFIRMED, SeekerChoice.DECLINE));
        assertEquals(InterviewResponse.CONFIRMED, InterviewStateMachine.responseFor(SeekerChoice.CONFIRM));
        assertEquals(InterviewResponse.DECLINED, InterviewStateMachine.responseFor(SeekerChoice.DECLINE));
    }

    @ParameterizedTest
    @EnumSource(value = InterviewStatus.class, names = {"DECLINED", "COMPLETED", "CANCELLED", "NO_SHOW"})
    void seekerCannotAnswerFromDeclinedOrFinalStatuses(InterviewStatus status) {
        for (SeekerChoice choice : SeekerChoice.values()) {
            assertInvalid(() -> InterviewStateMachine.afterResponse(status, choice));
        }
    }

    @Test
    void repeatedAnswersAreRecognisedAsIdempotent() {
        assertTrue(InterviewStateMachine.isRepeatedResponse(InterviewStatus.CONFIRMED, SeekerChoice.CONFIRM));
        assertTrue(InterviewStateMachine.isRepeatedResponse(InterviewStatus.DECLINED, SeekerChoice.DECLINE));
        assertFalse(InterviewStateMachine.isRepeatedResponse(InterviewStatus.CONFIRMED, SeekerChoice.DECLINE));
        assertFalse(InterviewStateMachine.isRepeatedResponse(InterviewStatus.DECLINED, SeekerChoice.CONFIRM));
        assertFalse(InterviewStateMachine.isRepeatedResponse(InterviewStatus.SCHEDULED, SeekerChoice.CONFIRM));
        assertFalse(InterviewStateMachine.isRepeatedResponse(InterviewStatus.CANCELLED, SeekerChoice.CONFIRM));
    }

    @Test
    void completionOutcomesMapToStatuses() {
        assertEquals(InterviewStatus.COMPLETED, InterviewStateMachine.afterCompletion(InterviewStatus.CONFIRMED, InterviewOutcome.COMPLETED));
        assertEquals(InterviewStatus.NO_SHOW, InterviewStateMachine.afterCompletion(InterviewStatus.SCHEDULED, InterviewOutcome.NO_SHOW));
    }

    @ParameterizedTest
    @EnumSource(value = InterviewStatus.class, names = {"DECLINED", "COMPLETED", "CANCELLED", "NO_SHOW"})
    void onlyScheduledOrConfirmedInterviewsCanBeCompleted(InterviewStatus status) {
        for (InterviewOutcome outcome : InterviewOutcome.values()) {
            assertInvalid(() -> InterviewStateMachine.afterCompletion(status, outcome));
        }
    }

    @Test
    void finalStatusesAreFinalAndDeclinedIsNot() {
        assertTrue(InterviewStatus.COMPLETED.isFinal());
        assertTrue(InterviewStatus.CANCELLED.isFinal());
        assertTrue(InterviewStatus.NO_SHOW.isFinal());
        assertFalse(InterviewStatus.DECLINED.isFinal());
        assertTrue(InterviewStatus.SCHEDULED.isActive());
        assertTrue(InterviewStatus.CONFIRMED.isActive());
        assertFalse(InterviewStatus.DECLINED.isActive());
    }

    private static void assertInvalid(org.junit.jupiter.api.function.Executable executable) {
        ConflictException e = assertThrows(ConflictException.class, executable);
        assertEquals(ErrorCode.INVALID_STATE_TRANSITION, e.errorCode());
    }
}
