package com.jobforge.backend.report.domain;

import com.jobforge.backend.shared.error.ConflictException;
import com.jobforge.backend.shared.error.ErrorCode;

/**
 * Report state machine. The contract only defines the resolve endpoint (API_CONTRACT 12.11), so the supported
 * transitions are OPEN|REVIEWING -> RESOLVED and OPEN|REVIEWING -> DISMISSED. RESOLVED and DISMISSED are terminal.
 * There is no endpoint that moves a report to REVIEWING; the status exists in the schema only.
 */
public final class ReportTransitions {

    private ReportTransitions() {}

    /** DISMISS closes the report as DISMISSED; every other action closes it as RESOLVED. */
    public static ReportStatus outcomeOf(ModerationAction action) {
        return action == ModerationAction.DISMISS ? ReportStatus.DISMISSED : ReportStatus.RESOLVED;
    }

    public static void requireResolvable(ReportStatus current) {
        if (!current.isActive()) {
            throw new ConflictException(ErrorCode.INVALID_STATE_TRANSITION,
                    "The report is already " + current + " and cannot be changed.");
        }
    }
}
