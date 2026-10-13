package com.jobforge.backend.shared.events;

/** Kafka topics from ARCHITECTURE §14 used by Dev 1 modules. */
public final class EventTopics {
    public static final String USERS = "jobforge.users.v1";
    public static final String JOBS = "jobforge.jobs.v1";
    public static final String APPLICATIONS = "jobforge.applications.v1";
    public static final String INTERVIEWS = "jobforge.interviews.v1";
    /** ARCHITECTURE 14: ContentReported, ContentModerated. */
    public static final String MODERATION = "jobforge.moderation.v1";

    private EventTopics() {}
}
