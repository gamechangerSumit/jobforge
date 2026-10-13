package com.jobforge.backend.analytics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.jobforge.backend.analytics.app.DateRange;
import com.jobforge.backend.shared.error.ValidationFailedException;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Proposed, unmerged Session 5 contract change (§12.10.1) date rules. Written but NOT executed in the authoring session. */
class DateRangeTest {

    private static final LocalDate TODAY = LocalDate.parse("2026-10-11");

    @Test
    void defaultsToLast30DaysEndingToday() {
        DateRange r = DateRange.resolve(null, null, TODAY);
        assertEquals(LocalDate.parse("2026-10-11"), r.to());
        assertEquals(LocalDate.parse("2026-09-12"), r.from());
        assertEquals(30, r.days().size());
    }

    @Test
    void boundsAreInclusiveUtcDays() {
        DateRange r = DateRange.resolve("2026-10-01", "2026-10-01", TODAY);
        assertEquals(Instant.parse("2026-10-01T00:00:00Z"), r.fromInstant());
        assertEquals(Instant.parse("2026-10-02T00:00:00Z"), r.toExclusiveInstant());
        assertEquals(1, r.days().size());
    }

    @Test
    void onlyFromGivenEndsToday() {
        DateRange r = DateRange.resolve("2026-10-01", null, TODAY);
        assertEquals(TODAY, r.to());
    }

    @Test
    void rejectsFromAfterTo() {
        assertThrows(ValidationFailedException.class, () -> DateRange.resolve("2026-10-05", "2026-10-04", TODAY));
    }

    @Test
    void rejectsMalformedDates() {
        assertThrows(ValidationFailedException.class, () -> DateRange.resolve("2026-10-1", null, TODAY));
        assertThrows(ValidationFailedException.class, () -> DateRange.resolve(null, "yesterday", TODAY));
        assertThrows(ValidationFailedException.class, () -> DateRange.resolve("2026-02-30", null, TODAY));
        assertThrows(ValidationFailedException.class, () -> DateRange.resolve("2026-10-01T00:00:00Z", null, TODAY));
    }

    @Test
    void reportsEveryInvalidField() {
        ValidationFailedException e = assertThrows(ValidationFailedException.class, () -> DateRange.resolve("x", "y", TODAY));
        assertEquals(2, e.details().size());
    }

    @Test
    void maxRangeIs366DaysInclusive() {
        DateRange ok = DateRange.resolve("2025-10-11", "2026-10-11", TODAY); // 366 days (inclusive)
        assertEquals(366, ok.days().size());
        assertThrows(ValidationFailedException.class, () -> DateRange.resolve("2025-10-10", "2026-10-11", TODAY));
    }
}
