package com.jobforge.backend.report.domain;

/** DATABASE_SCHEMA report_status. */
public enum ReportStatus {
    OPEN, REVIEWING, RESOLVED, DISMISSED;

    /** Active reports are covered by the one-open-report-per-reporter-and-target unique index. */
    public boolean isActive() {
        return this == OPEN || this == REVIEWING;
    }
}
