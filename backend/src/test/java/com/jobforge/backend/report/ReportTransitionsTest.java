package com.jobforge.backend.report;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jobforge.backend.report.domain.ModerationAction;
import com.jobforge.backend.report.domain.ReportStatus;
import com.jobforge.backend.report.domain.ReportTargetType;
import com.jobforge.backend.report.domain.ReportTransitions;
import com.jobforge.backend.shared.error.ConflictException;
import com.jobforge.backend.shared.error.ErrorCode;
import org.junit.jupiter.api.Test;

/** Pure unit test (no Spring, no Docker). NOT executed in the authoring session. */
class ReportTransitionsTest {

    @Test
    void dismissClosesAsDismissedEveryOtherActionAsResolved() {
        assertEquals(ReportStatus.DISMISSED, ReportTransitions.outcomeOf(ModerationAction.DISMISS));
        for (ModerationAction action : ModerationAction.values()) {
            if (action != ModerationAction.DISMISS) {
                assertEquals(ReportStatus.RESOLVED, ReportTransitions.outcomeOf(action), action.name());
            }
        }
    }

    @Test
    void onlyOpenAndReviewingAreResolvable() {
        assertDoesNotThrow(() -> ReportTransitions.requireResolvable(ReportStatus.OPEN));
        assertDoesNotThrow(() -> ReportTransitions.requireResolvable(ReportStatus.REVIEWING));
        for (ReportStatus terminal : new ReportStatus[] {ReportStatus.RESOLVED, ReportStatus.DISMISSED}) {
            ConflictException e = assertThrows(ConflictException.class, () -> ReportTransitions.requireResolvable(terminal));
            assertEquals(ErrorCode.INVALID_STATE_TRANSITION, e.errorCode());
        }
    }

    @Test
    void activeStatusesMatchTheUniqueIndexPredicate() {
        assertTrue(ReportStatus.OPEN.isActive());
        assertTrue(ReportStatus.REVIEWING.isActive());
        assertFalse(ReportStatus.RESOLVED.isActive());
        assertFalse(ReportStatus.DISMISSED.isActive());
    }

    @Test
    void auditEntityTypeNamesAreCapitalised() {
        assertEquals("Job", ReportTargetType.JOB.auditEntityType());
        assertEquals("Company", ReportTargetType.COMPANY.auditEntityType());
        assertEquals("User", ReportTargetType.USER.auditEntityType());
    }
}
