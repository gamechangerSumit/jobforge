package com.jobforge.backend.community.app;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Response DTOs (API_CONTRACT 12.9 Post DTO, 11 UserSummary). Comment/tag/share shapes: see SESSION4 contract text. */
public final class CommunityViews {

    private CommunityViews() {}

    public record AuthorView(UUID id, String handle, String firstName, String lastName, String avatarUrl, String role) {}

    public record Counts(int likes, int comments, int saves, int shares) {}

    public record PostViewer(boolean liked, boolean saved, boolean following) {}

    public record PostView(UUID id, String type, String title, String body, List<String> tags, JsonNode typeMetadata,
            AuthorView author, Counts counts, PostViewer viewer, String status, Instant createdAt, Instant editedAt) {}

    public record CommentCounts(int likes, int replies) {}

    public record CommentViewer(boolean liked) {}

    public record CommentView(UUID id, UUID postId, UUID parentId, String body, AuthorView author,
            CommentCounts counts, CommentViewer viewer, String status, Instant createdAt, Instant editedAt) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TagView(String slug, String name, Long postCount) {}

    public record ShareAck(String url) {}
}
