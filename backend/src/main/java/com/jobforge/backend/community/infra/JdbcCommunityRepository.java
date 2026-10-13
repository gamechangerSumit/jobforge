package com.jobforge.backend.community.infra;

import com.jobforge.backend.community.app.CommunityRecords.*;
import com.jobforge.backend.community.app.CommunityRepository;
import com.jobforge.backend.community.domain.ModerationState;
import com.jobforge.backend.community.domain.PostType;
import com.jobforge.backend.community.domain.ShareChannel;
import com.jobforge.backend.shared.persistence.Db;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Spring JDBC over schema {@code community} (DATABASE_SCHEMA 5). All SQL is parameterized; the only concatenated
 * fragments are fixed strings chosen from enums (counter column, sort order). Reads {@code core.users.status} only to
 * hide content of non-ACTIVE authors from feeds (community -> core direction, DATABASE_SCHEMA 8).
 */
@Repository
public class JdbcCommunityRepository implements CommunityRepository {

    private static final String POST_COLS = "p.id, p.author_user_id, p.type, p.title, p.body, "
            + "p.type_metadata::text AS type_metadata, p.status, p.like_count, p.comment_count, p.save_count, "
            + "p.share_count, p.created_at, p.edited_at, p.deleted_at, p.version";
    private static final String SCORE = "(p.like_count + 2 * p.comment_count + p.save_count)::bigint";
    private static final String PUBLIC_POST = "p.status = 'ACTIVE' AND p.deleted_at IS NULL AND EXISTS "
            + "(SELECT 1 FROM core.users u WHERE u.id = p.author_user_id AND u.status = 'ACTIVE')";
    private static final String COMMENT_COLS = "c.id, c.post_id, c.author_user_id, c.parent_comment_id, c.body, "
            + "c.status, c.like_count, c.created_at, c.edited_at, c.deleted_at";

    private final JdbcClient jdbc;

    public JdbcCommunityRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------ posts

    @Override
    public void insertPost(UUID id, UUID authorId, PostType type, String title, String body, String metadataJson, Instant now) {
        jdbc.sql("""
                INSERT INTO community.posts (id, author_user_id, type, title, body, type_metadata, created_at, updated_at)
                VALUES (:id, :author, :type, :title, :body, CAST(:meta AS jsonb), :now, :now)
                """)
                .param("id", id).param("author", authorId).param("type", type.name()).param("title", title)
                .param("body", body).param("meta", metadataJson).param("now", Db.ts(now)).update();
    }

    @Override
    public Optional<PostRow> findPost(UUID id) {
        return jdbc.sql("SELECT " + POST_COLS + ", p.created_at AS key_ts, " + SCORE
                        + " AS score FROM community.posts p WHERE p.id = :id AND p.deleted_at IS NULL")
                .param("id", id).query(JdbcCommunityRepository::mapPost).optional();
    }

    @Override
    public Optional<PostRow> findPostForUpdate(UUID id) {
        return jdbc.sql("SELECT " + POST_COLS + ", p.created_at AS key_ts, " + SCORE
                        + " AS score FROM community.posts p WHERE p.id = :id AND p.deleted_at IS NULL FOR UPDATE OF p")
                .param("id", id).query(JdbcCommunityRepository::mapPost).optional();
    }

    @Override
    public void updatePost(UUID id, String title, String body, String metadataJson, Instant editedAt) {
        jdbc.sql("""
                UPDATE community.posts
                   SET title = :title, body = :body, type_metadata = CAST(:meta AS jsonb), edited_at = :at,
                       version = version + 1
                 WHERE id = :id AND deleted_at IS NULL
                """)
                .param("title", title).param("body", body).param("meta", metadataJson).param("at", Db.ts(editedAt))
                .param("id", id).update();
    }

    @Override
    public boolean softDeletePost(UUID id, Instant at) {
        return jdbc.sql("UPDATE community.posts SET deleted_at = :at, version = version + 1 WHERE id = :id AND deleted_at IS NULL")
                .param("at", Db.ts(at)).param("id", id).update() == 1;
    }

    @Override
    public boolean transitionPost(UUID id, ModerationState from, ModerationState to) {
        return jdbc.sql("UPDATE community.posts SET status = :to, version = version + 1 "
                        + "WHERE id = :id AND status = :from AND deleted_at IS NULL")
                .param("to", to.name()).param("from", from.name()).param("id", id).update() == 1;
    }

    @Override
    public List<PostRow> feed(FeedFilter f, UUID viewerId, CursorPos after, int limit, Instant trendingSince) {
        StringBuilder sql = new StringBuilder("SELECT " + POST_COLS + ", p.created_at AS key_ts, " + SCORE
                + " AS score FROM community.posts p WHERE " + PUBLIC_POST);
        Map<String, Object> params = new LinkedHashMap<>();
        if (f.type() != null) {
            sql.append(" AND p.type = :type");
            params.put("type", f.type().name());
        }
        if (f.tagSlug() != null) {
            sql.append(" AND EXISTS (SELECT 1 FROM community.post_tags pt JOIN community.tags t ON t.id = pt.tag_id "
                    + "WHERE pt.post_id = p.id AND t.slug = :tag)");
            params.put("tag", f.tagSlug());
        }
        if (f.authorId() != null) {
            sql.append(" AND p.author_user_id = :author");
            params.put("author", f.authorId());
        }
        if (f.q() != null) {
            sql.append(" AND p.search_vector @@ websearch_to_tsquery('english', :q)");
            params.put("q", f.q());
        }
        if (f.feed() == FeedKind.FOLLOWING) {
            sql.append(" AND (p.author_user_id IN (SELECT followee_id FROM community.user_follows WHERE follower_id = :viewer)"
                    + " OR EXISTS (SELECT 1 FROM community.post_tags pt JOIN community.tag_follows tf ON tf.tag_id = pt.tag_id"
                    + " WHERE pt.post_id = p.id AND tf.user_id = :viewer))");
            params.put("viewer", viewerId);
        }
        if (f.feed() == FeedKind.TRENDING) {
            sql.append(" AND p.created_at >= :since");
            params.put("since", Db.ts(trendingSince));
            if (after != null) {
                sql.append(" AND (" + SCORE + ", p.created_at, p.id) < (:cs, :ct, :cid)");
                params.put("cs", after.score() == null ? 0L : after.score());
                params.put("ct", Db.ts(after.ts()));
                params.put("cid", after.id());
            }
            sql.append(" ORDER BY " + SCORE + " DESC, p.created_at DESC, p.id DESC");
        } else {
            if (after != null) {
                sql.append(" AND (p.created_at, p.id) < (:ct, :cid)");
                params.put("ct", Db.ts(after.ts()));
                params.put("cid", after.id());
            }
            sql.append(" ORDER BY p.created_at DESC, p.id DESC");
        }
        sql.append(" LIMIT :limit");
        params.put("limit", limit);
        return jdbc.sql(sql.toString()).params(params).query(JdbcCommunityRepository::mapPost).list();
    }

    @Override
    public List<PostRow> saved(UUID viewerId, CursorPos after, int limit) {
        StringBuilder sql = new StringBuilder("SELECT " + POST_COLS + ", ps.created_at AS key_ts, " + SCORE
                + " AS score FROM community.post_saves ps JOIN community.posts p ON p.id = ps.post_id "
                + "WHERE ps.user_id = :viewer AND " + PUBLIC_POST);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("viewer", viewerId);
        if (after != null) {
            sql.append(" AND (ps.created_at, p.id) < (:ct, :cid)");
            params.put("ct", Db.ts(after.ts()));
            params.put("cid", after.id());
        }
        sql.append(" ORDER BY ps.created_at DESC, p.id DESC LIMIT :limit");
        params.put("limit", limit);
        return jdbc.sql(sql.toString()).params(params).query(JdbcCommunityRepository::mapPost).list();
    }

    // ------------------------------------------------------------------ tags

    @Override
    public Map<String, UUID> upsertTags(Collection<String> slugs, Instant now) {
        for (String slug : slugs) {
            jdbc.sql("INSERT INTO community.tags (id, name, slug, created_at) VALUES (:id, :name, :slug, :now) "
                            + "ON CONFLICT (slug) DO NOTHING")
                    .param("id", UUID.randomUUID()).param("name", slug).param("slug", slug).param("now", Db.ts(now)).update();
        }
        Map<String, UUID> out = new LinkedHashMap<>();
        jdbc.sql("SELECT id, slug FROM community.tags WHERE slug IN (:slugs)").param("slugs", slugs)
                .query((rs, n) -> {
                    out.put(rs.getString("slug"), Db.uuid(rs, "id"));
                    return null;
                }).list();
        return out;
    }

    @Override
    public void replacePostTags(UUID postId, Collection<UUID> tagIds) {
        jdbc.sql("DELETE FROM community.post_tags WHERE post_id = :id").param("id", postId).update();
        for (UUID tagId : tagIds) {
            jdbc.sql("INSERT INTO community.post_tags (post_id, tag_id) VALUES (:p, :t) ON CONFLICT DO NOTHING")
                    .param("p", postId).param("t", tagId).update();
        }
    }

    @Override
    public Map<UUID, List<String>> tagSlugsOf(Collection<UUID> postIds) {
        Map<UUID, List<String>> out = new LinkedHashMap<>();
        if (postIds.isEmpty()) {
            return out;
        }
        jdbc.sql("SELECT pt.post_id, t.slug FROM community.post_tags pt JOIN community.tags t ON t.id = pt.tag_id "
                        + "WHERE pt.post_id IN (:ids) ORDER BY t.slug")
                .param("ids", postIds)
                .query((rs, n) -> {
                    out.computeIfAbsent(Db.uuid(rs, "post_id"), k -> new ArrayList<>()).add(rs.getString("slug"));
                    return null;
                }).list();
        return out;
    }

    @Override
    public Optional<TagRow> findTagBySlug(String slug) {
        return jdbc.sql("SELECT id, slug, name FROM community.tags WHERE slug = :slug").param("slug", slug)
                .query((rs, n) -> new TagRow(Db.uuid(rs, "id"), rs.getString("slug"), rs.getString("name"))).optional();
    }

    @Override
    public List<TagRow> searchTags(String prefix, int limit) {
        String safe = prefix.replaceAll("[^a-z0-9-]", "");
        if (safe.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("SELECT id, slug, name FROM community.tags WHERE slug LIKE :p ORDER BY slug LIMIT :n")
                .param("p", safe + "%").param("n", limit)
                .query((rs, n) -> new TagRow(Db.uuid(rs, "id"), rs.getString("slug"), rs.getString("name"))).list();
    }

    @Override
    public List<TrendingTag> trendingTags(Instant since, int limit) {
        return jdbc.sql("""
                SELECT t.slug, t.name, count(*) AS c
                  FROM community.post_tags pt
                  JOIN community.posts p ON p.id = pt.post_id
                  JOIN community.tags t ON t.id = pt.tag_id
                 WHERE p.status = 'ACTIVE' AND p.deleted_at IS NULL AND p.created_at >= :since
                 GROUP BY t.slug, t.name
                 ORDER BY c DESC, t.slug
                 LIMIT :n
                """).param("since", Db.ts(since)).param("n", limit)
                .query((rs, n) -> new TrendingTag(rs.getString("slug"), rs.getString("name"), rs.getLong("c"))).list();
    }

    // ------------------------------------------------------------------ viewer flags

    @Override
    public Set<UUID> likedPosts(UUID viewerId, Collection<UUID> postIds) {
        return ids("SELECT post_id AS id FROM community.post_likes WHERE user_id = :u AND post_id IN (:ids)", viewerId, postIds);
    }

    @Override
    public Set<UUID> savedPosts(UUID viewerId, Collection<UUID> postIds) {
        return ids("SELECT post_id AS id FROM community.post_saves WHERE user_id = :u AND post_id IN (:ids)", viewerId, postIds);
    }

    @Override
    public Set<UUID> followedUsers(UUID viewerId, Collection<UUID> userIds) {
        return ids("SELECT followee_id AS id FROM community.user_follows WHERE follower_id = :u AND followee_id IN (:ids)",
                viewerId, userIds);
    }

    @Override
    public Set<UUID> likedComments(UUID viewerId, Collection<UUID> commentIds) {
        return ids("SELECT comment_id AS id FROM community.comment_likes WHERE user_id = :u AND comment_id IN (:ids)",
                viewerId, commentIds);
    }

    private Set<UUID> ids(String sql, UUID viewerId, Collection<UUID> in) {
        if (in.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(jdbc.sql(sql).param("u", viewerId).param("ids", in)
                .query((rs, n) -> Db.uuid(rs, "id")).list());
    }

    // ------------------------------------------------------------------ interactions

    @Override
    public boolean insertPostLike(UUID postId, UUID userId, Instant now) {
        return jdbc.sql("INSERT INTO community.post_likes (post_id, user_id, created_at) VALUES (:p, :u, :now) ON CONFLICT DO NOTHING")
                .param("p", postId).param("u", userId).param("now", Db.ts(now)).update() == 1;
    }

    @Override
    public boolean deletePostLike(UUID postId, UUID userId) {
        return jdbc.sql("DELETE FROM community.post_likes WHERE post_id = :p AND user_id = :u")
                .param("p", postId).param("u", userId).update() == 1;
    }

    @Override
    public boolean insertPostSave(UUID postId, UUID userId, Instant now) {
        return jdbc.sql("INSERT INTO community.post_saves (post_id, user_id, created_at) VALUES (:p, :u, :now) ON CONFLICT DO NOTHING")
                .param("p", postId).param("u", userId).param("now", Db.ts(now)).update() == 1;
    }

    @Override
    public boolean deletePostSave(UUID postId, UUID userId) {
        return jdbc.sql("DELETE FROM community.post_saves WHERE post_id = :p AND user_id = :u")
                .param("p", postId).param("u", userId).update() == 1;
    }

    @Override
    public void insertShare(UUID id, UUID postId, UUID userId, ShareChannel channel, Instant now) {
        jdbc.sql("INSERT INTO community.post_shares (id, post_id, user_id, channel, created_at) VALUES (:id, :p, :u, :c, :now)")
                .param("id", id).param("p", postId).param("u", userId).param("c", channel.name()).param("now", Db.ts(now)).update();
    }

    @Override
    public void addPostCounter(UUID postId, PostCounter counter, int delta) {
        // column name comes from a closed enum, never from user input
        jdbc.sql("UPDATE community.posts SET " + counter.column() + " = GREATEST(" + counter.column() + " + :d, 0) WHERE id = :id")
                .param("d", delta).param("id", postId).update();
    }

    @Override
    public boolean insertCommentLike(UUID commentId, UUID userId, Instant now) {
        return jdbc.sql("INSERT INTO community.comment_likes (comment_id, user_id, created_at) VALUES (:c, :u, :now) ON CONFLICT DO NOTHING")
                .param("c", commentId).param("u", userId).param("now", Db.ts(now)).update() == 1;
    }

    @Override
    public boolean deleteCommentLike(UUID commentId, UUID userId) {
        return jdbc.sql("DELETE FROM community.comment_likes WHERE comment_id = :c AND user_id = :u")
                .param("c", commentId).param("u", userId).update() == 1;
    }

    @Override
    public void addCommentLikes(UUID commentId, int delta) {
        jdbc.sql("UPDATE community.comments SET like_count = GREATEST(like_count + :d, 0) WHERE id = :id")
                .param("d", delta).param("id", commentId).update();
    }

    // ------------------------------------------------------------------ comments

    @Override
    public void insertComment(UUID id, UUID postId, UUID authorId, UUID parentId, String body, Instant now) {
        jdbc.sql("""
                INSERT INTO community.comments (id, post_id, author_user_id, parent_comment_id, body, created_at, updated_at)
                VALUES (:id, :post, :author, :parent, :body, :now, :now)
                """)
                .param("id", id).param("post", postId).param("author", authorId).param("parent", parentId)
                .param("body", body).param("now", Db.ts(now)).update();
    }

    @Override
    public Optional<CommentRow> findComment(UUID id) {
        return jdbc.sql("SELECT " + COMMENT_COLS + " FROM community.comments c WHERE c.id = :id").param("id", id)
                .query(JdbcCommunityRepository::mapComment).optional();
    }

    @Override
    public Optional<CommentRow> findCommentForUpdate(UUID id) {
        return jdbc.sql("SELECT " + COMMENT_COLS + " FROM community.comments c WHERE c.id = :id FOR UPDATE OF c")
                .param("id", id).query(JdbcCommunityRepository::mapComment).optional();
    }

    @Override
    public void updateComment(UUID id, String body, Instant editedAt) {
        jdbc.sql("UPDATE community.comments SET body = :body, edited_at = :at WHERE id = :id AND deleted_at IS NULL")
                .param("body", body).param("at", Db.ts(editedAt)).param("id", id).update();
    }

    @Override
    public boolean softDeleteComment(UUID id, Instant at) {
        return jdbc.sql("UPDATE community.comments SET deleted_at = :at WHERE id = :id AND deleted_at IS NULL")
                .param("at", Db.ts(at)).param("id", id).update() == 1;
    }

    @Override
    public boolean transitionComment(UUID id, ModerationState from, ModerationState to) {
        return jdbc.sql("UPDATE community.comments SET status = :to WHERE id = :id AND status = :from AND deleted_at IS NULL")
                .param("to", to.name()).param("from", from.name()).param("id", id).update() == 1;
    }

    @Override
    public List<CommentRow> comments(UUID postId, UUID parentId, CursorPos after, int limit) {
        StringBuilder sql = new StringBuilder("SELECT " + COMMENT_COLS + " FROM community.comments c WHERE c.post_id = :post "
                + "AND c.status = 'ACTIVE' AND c.deleted_at IS NULL AND EXISTS "
                + "(SELECT 1 FROM core.users u WHERE u.id = c.author_user_id AND u.status = 'ACTIVE')");
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("post", postId);
        if (parentId == null) {
            sql.append(" AND c.parent_comment_id IS NULL");
        } else {
            sql.append(" AND c.parent_comment_id = :parent");
            params.put("parent", parentId);
        }
        if (after != null) {
            sql.append(" AND (c.created_at, c.id) > (:ct, :cid)");
            params.put("ct", Db.ts(after.ts()));
            params.put("cid", after.id());
        }
        sql.append(" ORDER BY c.created_at ASC, c.id ASC LIMIT :limit");
        params.put("limit", limit);
        return jdbc.sql(sql.toString()).params(params).query(JdbcCommunityRepository::mapComment).list();
    }

    @Override
    public Map<UUID, Integer> replyCounts(Collection<UUID> parentIds) {
        Map<UUID, Integer> out = new LinkedHashMap<>();
        if (parentIds.isEmpty()) {
            return out;
        }
        jdbc.sql("SELECT parent_comment_id, count(*) AS c FROM community.comments WHERE parent_comment_id IN (:ids) "
                        + "AND status = 'ACTIVE' AND deleted_at IS NULL GROUP BY parent_comment_id")
                .param("ids", parentIds)
                .query((rs, n) -> {
                    out.put(Db.uuid(rs, "parent_comment_id"), rs.getInt("c"));
                    return null;
                }).list();
        return out;
    }

    // ------------------------------------------------------------------ mentions

    @Override
    public Set<UUID> insertMentions(String sourceType, UUID sourceId, Collection<UUID> userIds, Instant now) {
        Set<UUID> fresh = new HashSet<>();
        for (UUID userId : userIds) {
            int rows = jdbc.sql("INSERT INTO community.mentions (id, source_type, source_id, mentioned_user_id, created_at) "
                            + "VALUES (:id, :t, :s, :u, :now) ON CONFLICT ON CONSTRAINT uq_mentions DO NOTHING")
                    .param("id", UUID.randomUUID()).param("t", sourceType).param("s", sourceId).param("u", userId)
                    .param("now", Db.ts(now)).update();
            if (rows == 1) {
                fresh.add(userId);
            }
        }
        return fresh;
    }

    @Override
    public void deleteMentionsExcept(String sourceType, UUID sourceId, Collection<UUID> keep) {
        if (keep.isEmpty()) {
            jdbc.sql("DELETE FROM community.mentions WHERE source_type = :t AND source_id = :s")
                    .param("t", sourceType).param("s", sourceId).update();
            return;
        }
        jdbc.sql("DELETE FROM community.mentions WHERE source_type = :t AND source_id = :s AND mentioned_user_id NOT IN (:keep)")
                .param("t", sourceType).param("s", sourceId).param("keep", keep).update();
    }

    // ------------------------------------------------------------------ follows

    @Override
    public boolean insertUserFollow(UUID followerId, UUID followeeId, Instant now) {
        return jdbc.sql("INSERT INTO community.user_follows (follower_id, followee_id, created_at) VALUES (:f, :e, :now) ON CONFLICT DO NOTHING")
                .param("f", followerId).param("e", followeeId).param("now", Db.ts(now)).update() == 1;
    }

    @Override
    public boolean deleteUserFollow(UUID followerId, UUID followeeId) {
        return jdbc.sql("DELETE FROM community.user_follows WHERE follower_id = :f AND followee_id = :e")
                .param("f", followerId).param("e", followeeId).update() == 1;
    }

    @Override
    public boolean insertTagFollow(UUID userId, UUID tagId, Instant now) {
        return jdbc.sql("INSERT INTO community.tag_follows (user_id, tag_id, created_at) VALUES (:u, :t, :now) ON CONFLICT DO NOTHING")
                .param("u", userId).param("t", tagId).param("now", Db.ts(now)).update() == 1;
    }

    @Override
    public boolean deleteTagFollow(UUID userId, UUID tagId) {
        return jdbc.sql("DELETE FROM community.tag_follows WHERE user_id = :u AND tag_id = :t")
                .param("u", userId).param("t", tagId).update() == 1;
    }

    // ------------------------------------------------------------------ consistency

    @Override
    public void recountComments(UUID postId) {
        jdbc.sql("SELECT id FROM community.posts WHERE id = :id FOR UPDATE").param("id", postId).query().listOfRows();
        jdbc.sql("""
                UPDATE community.posts p
                   SET comment_count = (
                       SELECT count(*)
                         FROM community.comments c
                        WHERE c.post_id = p.id AND c.status = 'ACTIVE' AND c.deleted_at IS NULL
                          AND EXISTS (SELECT 1 FROM core.users u WHERE u.id = c.author_user_id AND u.status = 'ACTIVE')
                          AND (c.parent_comment_id IS NULL OR EXISTS (
                                SELECT 1 FROM community.comments pc
                                 WHERE pc.id = c.parent_comment_id AND pc.status = 'ACTIVE' AND pc.deleted_at IS NULL
                                   AND EXISTS (SELECT 1 FROM core.users pu WHERE pu.id = pc.author_user_id AND pu.status = 'ACTIVE')))
                   )
                 WHERE p.id = :id
                """).param("id", postId).update();
    }

    @Override
    public List<UUID> postIdsCommentedBy(UUID userId) {
        return jdbc.sql("SELECT DISTINCT post_id FROM community.comments WHERE author_user_id = :u").param("u", userId)
                .query((rs, n) -> Db.uuid(rs, "post_id")).list();
    }

    @Override
    public void purgeInteractionsOf(UUID userId) {
        jdbc.sql("UPDATE community.posts SET like_count = GREATEST(like_count - 1, 0) "
                + "WHERE id IN (SELECT post_id FROM community.post_likes WHERE user_id = :u)").param("u", userId).update();
        jdbc.sql("DELETE FROM community.post_likes WHERE user_id = :u").param("u", userId).update();
        jdbc.sql("UPDATE community.posts SET save_count = GREATEST(save_count - 1, 0) "
                + "WHERE id IN (SELECT post_id FROM community.post_saves WHERE user_id = :u)").param("u", userId).update();
        jdbc.sql("DELETE FROM community.post_saves WHERE user_id = :u").param("u", userId).update();
        jdbc.sql("UPDATE community.comments SET like_count = GREATEST(like_count - 1, 0) "
                + "WHERE id IN (SELECT comment_id FROM community.comment_likes WHERE user_id = :u)").param("u", userId).update();
        jdbc.sql("DELETE FROM community.comment_likes WHERE user_id = :u").param("u", userId).update();
        jdbc.sql("DELETE FROM community.user_follows WHERE follower_id = :u OR followee_id = :u").param("u", userId).update();
        jdbc.sql("DELETE FROM community.tag_follows WHERE user_id = :u").param("u", userId).update();
        jdbc.sql("DELETE FROM community.company_follows WHERE user_id = :u").param("u", userId).update();
        jdbc.sql("DELETE FROM community.mentions WHERE mentioned_user_id = :u").param("u", userId).update();
    }

    @Override
    public int countPostsSince(UUID authorId, Instant since) {
        Integer n = jdbc.sql("SELECT count(*) FROM community.posts WHERE author_user_id = :a AND created_at >= :s")
                .param("a", authorId).param("s", Db.ts(since)).query(Integer.class).single();
        return n == null ? 0 : n;
    }

    @Override
    public int countCommentsSince(UUID authorId, Instant since) {
        Integer n = jdbc.sql("SELECT count(*) FROM community.comments WHERE author_user_id = :a AND created_at >= :s")
                .param("a", authorId).param("s", Db.ts(since)).query(Integer.class).single();
        return n == null ? 0 : n;
    }

    @Override
    public boolean postBodyExistsSince(UUID authorId, String body, Instant since) {
        return Boolean.TRUE.equals(jdbc.sql("SELECT EXISTS (SELECT 1 FROM community.posts WHERE author_user_id = :a "
                        + "AND created_at >= :s AND lower(btrim(body)) = lower(btrim(:b)))")
                .param("a", authorId).param("s", Db.ts(since)).param("b", body).query(Boolean.class).single());
    }

    @Override
    public boolean commentBodyExistsSince(UUID authorId, String body, Instant since) {
        return Boolean.TRUE.equals(jdbc.sql("SELECT EXISTS (SELECT 1 FROM community.comments WHERE author_user_id = :a "
                        + "AND created_at >= :s AND lower(btrim(body)) = lower(btrim(:b)))")
                .param("a", authorId).param("s", Db.ts(since)).param("b", body).query(Boolean.class).single());
    }

    // ------------------------------------------------------------------ mapping

    private static PostRow mapPost(ResultSet rs, int rowNum) throws SQLException {
        return new PostRow(Db.uuid(rs, "id"), Db.uuid(rs, "author_user_id"), PostType.valueOf(rs.getString("type")),
                rs.getString("title"), rs.getString("body"), rs.getString("type_metadata"),
                ModerationState.valueOf(rs.getString("status")), rs.getInt("like_count"), rs.getInt("comment_count"),
                rs.getInt("save_count"), rs.getInt("share_count"), Db.instant(rs, "created_at"),
                Db.instant(rs, "edited_at"), Db.instant(rs, "deleted_at"), rs.getLong("version"),
                Db.instant(rs, "key_ts"), rs.getLong("score"));
    }

    private static CommentRow mapComment(ResultSet rs, int rowNum) throws SQLException {
        return new CommentRow(Db.uuid(rs, "id"), Db.uuid(rs, "post_id"), Db.uuid(rs, "author_user_id"),
                Db.uuid(rs, "parent_comment_id"), rs.getString("body"), ModerationState.valueOf(rs.getString("status")),
                rs.getInt("like_count"), Db.instant(rs, "created_at"), Db.instant(rs, "edited_at"),
                Db.instant(rs, "deleted_at"));
    }
}
