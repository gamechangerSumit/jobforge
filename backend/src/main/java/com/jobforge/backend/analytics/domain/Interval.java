package com.jobforge.backend.analytics.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

/** Time-series bucket size for the proposed, unmerged Session 5 contract change (API_CONTRACT §12.11.2). Buckets are UTC calendar buckets; WEEK starts on Monday (ISO). */
public enum Interval {
    DAY("day"),
    WEEK("week"),
    MONTH("month");

    /** Upper bound on buckets per response for the proposed, unmerged Session 5 contract change (API_CONTRACT §12.11.2). */
    public static final int MAX_BUCKETS = 400;

    private final String sqlUnit;

    Interval(String sqlUnit) {
        this.sqlUnit = sqlUnit;
    }

    /** Value bound as the {@code date_trunc} unit parameter (whitelisted constant, never user text). */
    public String sqlUnit() {
        return sqlUnit;
    }

    /** First day of the bucket containing {@code day}. */
    public LocalDate bucketStart(LocalDate day) {
        return switch (this) {
            case DAY -> day;
            case WEEK -> day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTH -> day.withDayOfMonth(1);
        };
    }

    private LocalDate next(LocalDate bucketStart) {
        return switch (this) {
            case DAY -> bucketStart.plusDays(1);
            case WEEK -> bucketStart.plusWeeks(1);
            case MONTH -> bucketStart.plusMonths(1);
        };
    }

    /** Key of a bucket: its start date, but never earlier than {@code from} (the first bucket is clipped). */
    public LocalDate key(LocalDate bucketStart, LocalDate from) {
        return bucketStart.isBefore(from) ? from : bucketStart;
    }

    /** Number of buckets covering {@code [from, to]}; cheap, used to enforce {@link #MAX_BUCKETS} before querying. */
    public long bucketCount(LocalDate from, LocalDate to) {
        long count = 0;
        for (LocalDate b = bucketStart(from); !b.isAfter(to); b = next(b)) {
            count++;
            if (count > MAX_BUCKETS) {
                return count;
            }
        }
        return count;
    }

    /** Ascending, zero-fill keys of every bucket covering {@code [from, to]}. */
    public List<LocalDate> keys(LocalDate from, LocalDate to) {
        List<LocalDate> keys = new ArrayList<>();
        for (LocalDate b = bucketStart(from); !b.isAfter(to); b = next(b)) {
            keys.add(key(b, from));
        }
        return keys;
    }
}
