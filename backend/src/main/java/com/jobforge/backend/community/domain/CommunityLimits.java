package com.jobforge.backend.community.domain;

/** API_CONTRACT 10 limits for community content. */
public final class CommunityLimits {
    public static final int TITLE_MAX = 150;
    public static final int BODY_MAX = 10_000;
    public static final int COMMENT_MAX = 2_000;
    public static final int TAGS_MAX = 5;
    public static final int COMMENT_MAX_DEPTH = 2;
    public static final int CURSOR_DEFAULT_LIMIT = 20;
    public static final int CURSOR_MAX_LIMIT = 50;

    // anti-spam (Session 4 fix pass; API_CONTRACT 12.9.2). ADMIN is exempt.
    public static final int MAX_LINKS_POST = 3;
    public static final int MAX_LINKS_COMMENT = 2;
    public static final int POSTS_PER_10_MIN = 5;
    public static final int POSTS_PER_DAY = 30;
    public static final int COMMENTS_PER_10_MIN = 20;
    public static final int COMMENTS_PER_DAY = 200;
    public static final int DUPLICATE_WINDOW_HOURS = 24;

    private CommunityLimits() {}
}
