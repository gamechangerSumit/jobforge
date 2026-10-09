package com.jobforge.backend.interview.domain;

import com.jobforge.backend.shared.error.BusinessRuleException;
import com.jobforge.backend.shared.error.FieldErrorDetail;
import com.jobforge.backend.shared.error.ValidationFailedException;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

/** Scheduling rules. Limits mirror DATABASE_SCHEMA §4.5 (duration 15..480 minutes, location/link ≤500 characters). */
public final class InterviewScheduling {

    public static final int MIN_DURATION_MINUTES = 15;
    public static final int MAX_DURATION_MINUTES = 480;
    /** Sanity bound: interviews are not booked more than a year ahead. */
    public static final Duration MAX_ADVANCE = Duration.ofDays(365);

    private InterviewScheduling() {}

    /** @throws ValidationFailedException 400 when the value is not an IANA zone id such as {@code Asia/Kolkata} */
    public static String requireTimezone(String timezone) {
        try {
            return ZoneId.of(timezone).getId();
        } catch (DateTimeException | NullPointerException e) {
            throw ValidationFailedException.of("timezone", "PATTERN", "must be a valid IANA time zone, for example Asia/Kolkata");
        }
    }

    /** @throws BusinessRuleException 422 when the slot is not in the future or too far ahead */
    public static void requireBookable(Instant scheduledAt, Instant now) {
        if (!scheduledAt.isAfter(now)) {
            throw new BusinessRuleException("The interview must be scheduled in the future.",
                    List.of(new FieldErrorDetail("scheduledAt", "BUSINESS_RULE", "must be in the future")));
        }
        if (scheduledAt.isAfter(now.plus(MAX_ADVANCE))) {
            throw new BusinessRuleException("The interview cannot be scheduled more than one year ahead.",
                    List.of(new FieldErrorDetail("scheduledAt", "BUSINESS_RULE", "must be within one year")));
        }
    }

    public static boolean overlaps(Instant startA, Instant endA, Instant startB, Instant endB) {
        return startA.isBefore(endB) && startB.isBefore(endA);
    }

    /** @throws BusinessRuleException 422 once the interview has started (the seeker can no longer answer) */
    public static void requireNotStarted(Interview interview, Instant now) {
        if (!interview.scheduledAt().isAfter(now)) {
            throw new BusinessRuleException("This interview has already started.");
        }
    }

    /** @throws BusinessRuleException 422 before the interview starts (it cannot be completed or marked a no-show yet) */
    public static void requireStarted(Interview interview, Instant now) {
        if (interview.scheduledAt().isAfter(now)) {
            throw new BusinessRuleException("The interview has not started yet.");
        }
    }
}
