package com.jobforge.backend.analytics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jobforge.backend.analytics.domain.Interval;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Proposed, unmerged Session 5 contract change (§12.11.2): bucket keys, clipping and the 400-bucket guard. */
class IntervalTest {

    private static LocalDate d(String s) {
        return LocalDate.parse(s);
    }

    @Test
    void dayKeysAreEveryDay() {
        assertEquals(List.of(d("2026-10-01"), d("2026-10-02"), d("2026-10-03")), Interval.DAY.keys(d("2026-10-01"), d("2026-10-03")));
    }

    @Test
    void weekBucketsStartOnMondayAndFirstIsClippedToFrom() {
        // 2026-10-07 is a Wednesday; the Monday of that week is 2026-10-05.
        assertEquals(d("2026-10-05"), Interval.WEEK.bucketStart(d("2026-10-07")));
        assertEquals(List.of(d("2026-10-07"), d("2026-10-12"), d("2026-10-19")), Interval.WEEK.keys(d("2026-10-07"), d("2026-10-20")));
    }

    @Test
    void monthBucketsAreClippedAtFrom() {
        assertEquals(List.of(d("2026-09-15"), d("2026-10-01"), d("2026-11-01")), Interval.MONTH.keys(d("2026-09-15"), d("2026-11-02")));
    }

    @Test
    void keyNeverPrecedesFrom() {
        assertEquals(d("2026-10-07"), Interval.WEEK.key(d("2026-10-05"), d("2026-10-07")));
        assertEquals(d("2026-10-12"), Interval.WEEK.key(d("2026-10-12"), d("2026-10-07")));
    }

    @Test
    void bucketCountStopsAboveTheLimit() {
        assertEquals(366, Interval.DAY.bucketCount(d("2025-10-11"), d("2026-10-11")));
        assertTrue(Interval.DAY.bucketCount(d("2020-01-01"), d("2026-10-11")) > Interval.MAX_BUCKETS);
    }
}
