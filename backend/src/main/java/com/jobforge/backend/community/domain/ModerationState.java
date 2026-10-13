package com.jobforge.backend.community.domain;

/** DATABASE_SCHEMA 3.7 post_status / comment_status (moderation state; user deletion uses deleted_at). */
public enum ModerationState { ACTIVE, HIDDEN, REMOVED }
