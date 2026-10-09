package com.jobforge.backend.interview.domain;

import static com.jobforge.backend.interview.domain.InterviewStatus.COMPLETED;
import static com.jobforge.backend.interview.domain.InterviewStatus.CONFIRMED;
import static com.jobforge.backend.interview.domain.InterviewStatus.DECLINED;
import static com.jobforge.backend.interview.domain.InterviewStatus.NO_SHOW;
import static com.jobforge.backend.interview.domain.InterviewStatus.SCHEDULED;

import com.jobforge.backend.shared.error.ConflictException;
import com.jobforge.backend.shared.error.ErrorCode;
import java.util.List;

/**
 * Interview transition rules (409 INVALID_STATE_TRANSITION on violation).
 * <pre>
 * SCHEDULED  --seeker CONFIRM--> CONFIRMED      SCHEDULED/CONFIRMED --seeker DECLINE--> DECLINED
 * SCHEDULED/CONFIRMED/DECLINED --recruiter PATCH (seeker-visible change)--> SCHEDULED (response reset to PENDING)
 * SCHEDULED/CONFIRMED/DECLINED --recruiter cancel--> CANCELLED
 * SCHEDULED/CONFIRMED --recruiter complete--> COMPLETED | NO_SHOW
 * COMPLETED, CANCELLED, NO_SHOW are final.
 * </pre>
 */
public final class InterviewStateMachine {

    private static final List<InterviewStatus> EDITABLE = List.of(SCHEDULED, CONFIRMED, DECLINED);
    private static final List<InterviewStatus> CANCELLABLE = List.of(SCHEDULED, CONFIRMED, DECLINED);
    private static final List<InterviewStatus> RESPONDABLE = List.of(SCHEDULED, CONFIRMED);
    private static final List<InterviewStatus> COMPLETABLE = List.of(SCHEDULED, CONFIRMED);

    private InterviewStateMachine() {}

    public static boolean canEdit(InterviewStatus from) {
        return EDITABLE.contains(from);
    }

    public static boolean canCancel(InterviewStatus from) {
        return CANCELLABLE.contains(from);
    }

    public static boolean canComplete(InterviewStatus from) {
        return COMPLETABLE.contains(from);
    }

    public static void requireEditable(InterviewStatus from) {
        if (!canEdit(from)) {
            throw invalid(from, "be edited or rescheduled");
        }
    }

    public static void requireCancellable(InterviewStatus from) {
        if (!canCancel(from)) {
            throw invalid(from, "be cancelled");
        }
    }

    /** @return CONFIRMED for CONFIRM, DECLINED for DECLINE */
    public static InterviewStatus afterResponse(InterviewStatus from, SeekerChoice choice) {
        if (!RESPONDABLE.contains(from)) {
            throw invalid(from, "be answered");
        }
        return choice == SeekerChoice.CONFIRM ? CONFIRMED : DECLINED;
    }

    /** True when the seeker repeats the answer already stored (idempotent replay, no state change). */
    public static boolean isRepeatedResponse(InterviewStatus current, SeekerChoice choice) {
        return (current == CONFIRMED && choice == SeekerChoice.CONFIRM) || (current == DECLINED && choice == SeekerChoice.DECLINE);
    }

    public static InterviewStatus afterCompletion(InterviewStatus from, InterviewOutcome outcome) {
        if (!canComplete(from)) {
            throw invalid(from, "be marked " + outcome);
        }
        return outcome == InterviewOutcome.COMPLETED ? COMPLETED : NO_SHOW;
    }

    public static InterviewResponse responseFor(SeekerChoice choice) {
        return choice == SeekerChoice.CONFIRM ? InterviewResponse.CONFIRMED : InterviewResponse.DECLINED;
    }

    private static ConflictException invalid(InterviewStatus from, String what) {
        return new ConflictException(ErrorCode.INVALID_STATE_TRANSITION, "An interview in status " + from + " cannot " + what + ".");
    }
}
