package com.jobforge.backend.shared.audit;

/** Action names from DATABASE_SCHEMA §13 (only those used so far; add as features land). */
public final class AuditAction {
    public static final String USER_REGISTERED = "USER_REGISTERED";
    public static final String USER_LOGIN_SUCCESS = "USER_LOGIN_SUCCESS";
    public static final String USER_LOGIN_FAILED = "USER_LOGIN_FAILED";
    public static final String USER_LOGOUT = "USER_LOGOUT";
    public static final String TOKEN_REUSE_DETECTED = "TOKEN_REUSE_DETECTED";
    public static final String PASSWORD_CHANGED = "PASSWORD_CHANGED";
    public static final String PASSWORD_RESET = "PASSWORD_RESET";
    public static final String EMAIL_VERIFIED = "EMAIL_VERIFIED";

    public static final String JOB_CREATED = "JOB_CREATED";
    public static final String JOB_UPDATED = "JOB_UPDATED";
    public static final String JOB_PUBLISHED = "JOB_PUBLISHED";
    public static final String JOB_UNPUBLISHED = "JOB_UNPUBLISHED";
    public static final String JOB_CLOSED = "JOB_CLOSED";
    public static final String JOB_DELETED = "JOB_DELETED";
    public static final String JOB_REMOVED = "JOB_REMOVED";
    /** DATABASE_SCHEMA 13 audit catalog: report lifecycle and consequential moderation outcomes. */
    public static final String REPORT_FILED = "REPORT_FILED";
    public static final String REPORT_RESOLVED = "REPORT_RESOLVED";
    public static final String CONTENT_MODERATED = "CONTENT_MODERATED";
    public static final String JOB_RESTORED = "JOB_RESTORED";
    public static final String APPLICATION_SUBMITTED = "APPLICATION_SUBMITTED";
    public static final String APPLICATION_STATUS_CHANGED = "APPLICATION_STATUS_CHANGED";
    public static final String APPLICATION_WITHDRAWN = "APPLICATION_WITHDRAWN";
    public static final String CANDIDATE_PROFILE_VIEWED = "CANDIDATE_PROFILE_VIEWED";
    public static final String INTERVIEW_SCHEDULED = "INTERVIEW_SCHEDULED";
    public static final String INTERVIEW_CANCELLED = "INTERVIEW_CANCELLED";

    public static final String COMPANY_CREATED = "COMPANY_CREATED";
    public static final String COMPANY_UPDATED = "COMPANY_UPDATED";
    public static final String COMPANY_VERIFIED = "COMPANY_VERIFIED";
    public static final String COMPANY_REJECTED = "COMPANY_REJECTED";
    public static final String COMPANY_SUSPENDED = "COMPANY_SUSPENDED";
    public static final String COMPANY_MEMBER_ADDED = "COMPANY_MEMBER_ADDED";
    public static final String COMPANY_MEMBER_REMOVED = "COMPANY_MEMBER_REMOVED";
    public static final String RECRUITER_APPROVED = "RECRUITER_APPROVED";
    public static final String RECRUITER_REJECTED = "RECRUITER_REJECTED";
    public static final String USER_STATUS_CHANGED = "USER_STATUS_CHANGED";
    public static final String ADMIN_CREATED = "ADMIN_CREATED";
    public static final String USER_AVATAR_CHANGED = "USER_AVATAR_CHANGED";
    public static final String USER_DELETED = "USER_DELETED";
    public static final String COMPANY_LOGO_CHANGED = "COMPANY_LOGO_CHANGED";

    private AuditAction() {}
}
