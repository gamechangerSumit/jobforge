package com.jobforge.backend.community.app;

import com.jobforge.backend.community.domain.ModerationState;
import com.jobforge.backend.community.domain.PostType;
import java.time.Instant;
import java.util.UUID;

/** Persistence rows and query inputs of the community module (internal; never serialized to clients). */
public final class CommunityRecords {

    private CommunityRecords() {}

    public enum FeedKind { LATEST, FOLLOWING, TRENDING }

    /** {@code keyTs}/{@code score} are the keyset-pagination keys of the query that produced the row. */
    public record PostRow(UUID id, UUID authorId, PostType type, String title, String body, String typeMetadataJson,
            ModerationState status, int likes, int comments, int saves, int shares, Instant createdAt,
            Instant editedAt, Instant deletedAt, long version, Instant keyTs, long score) {}

    public record CommentRow(UUID id, UUID postId, UUID authorId, UUID parentId, String body,
            ModerationState status, int likes, Instant createdAt, Instant editedAt, Instant deletedAt) {}

    /** Keyset position: ({@code score}, {@code ts}, {@code id}); {@code score} is null for time-ordered lists. */
    public record CursorPos(Instant ts, Long score, UUID id) {}

    public record FeedFilter(FeedKind feed, PostType type, String tagSlug, UUID authorId, String q) {}

    public record TagRow(UUID id, String slug, String name) {}

    public record TrendingTag(String slug, String name, long postCount) {}

    public enum PostCounter { LIKES("like_count"), COMMENTS("comment_count"), SAVES("save_count"), SHARES("share_count");
        private final String column;
        PostCounter(String column) { this.column = column; }
        public String column() { return column; }
    }
}
