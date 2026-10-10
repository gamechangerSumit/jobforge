package com.jobforge.backend.report.domain;

/** DATABASE_SCHEMA report_target_type. POST/COMMENT belong to the community module (not part of this session). */
public enum ReportTargetType {
    POST, COMMENT, USER, JOB, COMPANY;

    /** Entity type name used by the audit trail ("Job", "User", ...), consistent with the other modules. */
    public String auditEntityType() {
        String lower = name().toLowerCase();
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
