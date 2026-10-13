package com.jobforge.backend.community.app;

import java.util.UUID;

/**
 * Payloads of events published on {@code jobforge.community.v1} (ARCHITECTURE 14: PostCreated, CommentCreated,
 * PostLiked, UserMentioned, UserFollowed). No free text (bodies, titles) is ever put in a payload.
 */
public final class CommunityEvents {

    private CommunityEvents() {}

    public record PostCreated(UUID postId, UUID authorId, String type) {}

    public record CommentCreated(UUID commentId, UUID postId, UUID authorId, UUID parentCommentId, UUID postAuthorId,
            UUID parentAuthorId) {}

    public record PostLiked(UUID postId, UUID likerId, UUID postAuthorId) {}

    public record UserMentioned(String sourceType, UUID sourceId, UUID postId, UUID mentionedUserId, UUID authorId) {}

    public record UserFollowed(UUID followerId, UUID followeeId) {}
}
