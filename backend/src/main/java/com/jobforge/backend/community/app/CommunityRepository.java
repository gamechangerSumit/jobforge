package com.jobforge.backend.community.app;

import com.jobforge.backend.community.app.CommunityRecords.*;
import com.jobforge.backend.community.domain.ModerationState;
import com.jobforge.backend.community.domain.PostType;
import com.jobforge.backend.community.domain.ShareChannel;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Persistence port over schema {@code community} (DATABASE_SCHEMA 5). Implemented with Spring JDBC in infra. */
public interface CommunityRepository {

    // ---- posts
    void insertPost(UUID id, UUID authorId, PostType type, String title, String body, String metadataJson, Instant now);

    /** Not soft-deleted rows only. Moderation state is returned; callers decide visibility. */
    Optional<PostRow> findPost(UUID id);

    Optional<PostRow> findPostForUpdate(UUID id);

    void updatePost(UUID id, String title, String body, String metadataJson, Instant editedAt);

    boolean softDeletePost(UUID id, Instant at);

    boolean transitionPost(UUID id, ModerationState from, ModerationState to);

    List<PostRow> feed(FeedFilter filter, UUID viewerId, CursorPos after, int limit, Instant trendingSince);

    List<PostRow> saved(UUID viewerId, CursorPos after, int limit);

    // ---- tags
    /** Creates missing tags; returns slug -> id for all given slugs. */
    Map<String, UUID> upsertTags(Collection<String> slugs, Instant now);

    void replacePostTags(UUID postId, Collection<UUID> tagIds);

    Map<UUID, List<String>> tagSlugsOf(Collection<UUID> postIds);

    Optional<TagRow> findTagBySlug(String slug);

    List<TagRow> searchTags(String prefix, int limit);

    List<TrendingTag> trendingTags(Instant since, int limit);

    // ---- viewer flags
    Set<UUID> likedPosts(UUID viewerId, Collection<UUID> postIds);

    Set<UUID> savedPosts(UUID viewerId, Collection<UUID> postIds);

    Set<UUID> followedUsers(UUID viewerId, Collection<UUID> userIds);

    Set<UUID> likedComments(UUID viewerId, Collection<UUID> commentIds);

    // ---- interactions (insert/delete return whether a row changed; counters are adjusted by the caller, same tx)
    boolean insertPostLike(UUID postId, UUID userId, Instant now);

    boolean deletePostLike(UUID postId, UUID userId);

    boolean insertPostSave(UUID postId, UUID userId, Instant now);

    boolean deletePostSave(UUID postId, UUID userId);

    void insertShare(UUID id, UUID postId, UUID userId, ShareChannel channel, Instant now);

    void addPostCounter(UUID postId, PostCounter counter, int delta);

    boolean insertCommentLike(UUID commentId, UUID userId, Instant now);

    boolean deleteCommentLike(UUID commentId, UUID userId);

    void addCommentLikes(UUID commentId, int delta);

    // ---- comments
    void insertComment(UUID id, UUID postId, UUID authorId, UUID parentId, String body, Instant now);

    Optional<CommentRow> findComment(UUID id);

    Optional<CommentRow> findCommentForUpdate(UUID id);

    void updateComment(UUID id, String body, Instant editedAt);

    boolean softDeleteComment(UUID id, Instant at);

    boolean transitionComment(UUID id, ModerationState from, ModerationState to);

    /** Visible (ACTIVE, not deleted) comments of one post, oldest first; {@code parentId} null = top-level. */
    List<CommentRow> comments(UUID postId, UUID parentId, CursorPos after, int limit);

    Map<UUID, Integer> replyCounts(Collection<UUID> parentIds);

    // ---- mentions
    /** Inserts missing mentions; returns the users that were newly mentioned. */
    Set<UUID> insertMentions(String sourceType, UUID sourceId, Collection<UUID> userIds, Instant now);

    void deleteMentionsExcept(String sourceType, UUID sourceId, Collection<UUID> keep);

    // ---- follows
    boolean insertUserFollow(UUID followerId, UUID followeeId, Instant now);

    boolean deleteUserFollow(UUID followerId, UUID followeeId);

    boolean insertTagFollow(UUID userId, UUID tagId, Instant now);

    boolean deleteTagFollow(UUID userId, UUID tagId);

    // ---- consistency (Session 4 fix pass)
    /**
     * Locks the post row, then recomputes {@code comment_count} = visible comments (ACTIVE, not deleted, author ACTIVE,
     * and for replies an equally visible parent). Exact after deletes, moderation, parent cascades and account deletion.
     */
    void recountComments(UUID postId);

    /** Posts on which the user has written a comment (any state). */
    List<UUID> postIdsCommentedBy(UUID userId);

    /**
     * Hard-deletes the personal interactions of a deleted account (DATABASE_SCHEMA 11): post/comment likes, saves,
     * user/tag/company follows (both directions) and received mentions; adjusts the like/save counters first.
     */
    void purgeInteractionsOf(UUID userId);

    // ---- anti-spam queries (count deleted rows too, so deleting cannot be used to dodge the limits)
    int countPostsSince(UUID authorId, Instant since);

    int countCommentsSince(UUID authorId, Instant since);

    boolean postBodyExistsSince(UUID authorId, String body, Instant since);

    boolean commentBodyExistsSince(UUID authorId, String body, Instant since);
}
