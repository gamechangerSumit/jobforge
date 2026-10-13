package com.jobforge.backend.analytics.app;

import com.jobforge.backend.shared.error.FieldErrorDetail;
import com.jobforge.backend.shared.error.ValidationFailedException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Proposed (Session 5, unmerged contract change; API_CONTRACT §12.10.1): inclusive UTC calendar-date range. A day is {@code [00:00Z, 24:00Z)}; the query filter is
 * {@code ts >= from 00:00Z AND ts < (to + 1 day) 00:00Z}.
 */
public record DateRange(LocalDate from, LocalDate to) {

    public static final int DEFAULT_DAYS = 30;
    public static final int MAX_DAYS = 366;

    /**
     * Resolves optional raw {@code from}/{@code to} values. Defaults: {@code to} = today (UTC), {@code from} =
     * {@code to - 29 days}. Invalid formats, {@code from > to} and ranges over {@link #MAX_DAYS} → 400.
     */
    public static DateRange resolve(String fromRaw, String toRaw, LocalDate today) {
        List<FieldErrorDetail> errors = new ArrayList<>();
        LocalDate to = parse("to", toRaw, errors);
        LocalDate from = parse("from", fromRaw, errors);
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }
        if (to == null) {
            to = today;
        }
        if (from == null) {
            from = to.minusDays(DEFAULT_DAYS - 1L);
        }
        if (from.isAfter(to)) {
            throw ValidationFailedException.of("from", "MAX", "must not be after 'to'");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS) {
            throw ValidationFailedException.of("from", "MAX", "range must not exceed " + MAX_DAYS + " days");
        }
        return new DateRange(from, to);
    }

    private static LocalDate parse(String field, String raw, List<FieldErrorDetail> errors) {
        if (raw == null) {
            return null;
        }
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException e) {
            errors.add(new FieldErrorDetail(field, "PATTERN", "must be a date in YYYY-MM-DD format"));
            return null;
        }
    }

    /** Inclusive lower bound. */
    public Instant fromInstant() {
        return from.atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    /** Exclusive upper bound: start of the day after {@code to}. */
    public Instant toExclusiveInstant() {
        return to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    public List<LocalDate> days() {
        return from.datesUntil(to.plusDays(1)).toList();
    }
}
