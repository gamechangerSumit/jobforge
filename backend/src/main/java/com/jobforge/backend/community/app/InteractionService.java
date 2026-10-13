package com.jobforge.backend.community.app;

import com.jobforge.backend.community.app.CommunityEvents.PostLiked;
import com.jobforge.backend.community.app.CommunityEvents.UserFollowed;
import com.jobforge.backend.community.app.CommunityRecords.CommentRow;
import com.jobforge.backend.community.app.CommunityRecords.PostCounter;
import com.jobforge.backend.community.app.CommunityRecords.PostRow;
import com.jobforge.backend.community.app.CommunityRecords.TagRow;
import com.jobforge.backend.community.app.CommunityRecords.TrendingTag;
import com.jobforge.backend.community.app.CommunityViews.ShareAck;
import com.jobforge.backend.community.app.CommunityViews.TagView;
import com.jobforge.backend.community.domain.ShareChannel;
import com.jobforge.backend.community.domain.Tags;
import com.jobforge.backend.shared.error.BusinessRuleException;
import com.jobforge.backend.shared.error.ResourceNotFoundException;
import com.jobforge.backend.shared.error.ValidationFailedException;
import com.jobforge.backend.shared.events.DomainEvent;
import com.jobforge.backend.shared.events.EventPublisher;
import com.jobforge.backend.shared.events.EventTopics;
import com.jobforge.backend.shared.security.AuthenticatedUser;
import com.jobforge.backend.user.facade.UserSummaryFacade;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Likes, saves, shares, follows and tags. Like/save/follow are idempotent set/unset (API_CONTRACT 4: 204): a repeated
 * PUT/DELETE changes nothing. Counters move only when a row was really inserted/deleted, in the same transaction, so
 * double clicks and races cannot drift them (the PK enforces uniqueness; counter updates are atomic SQL increments).
 */
@Service
public class InteractionService {

    private static final int TAG_LIST_LIMIT = 10;
    private static final int TRENDING_DAYS = 7;

    private final CommunityRepository repository;
    private final PostAccess postAccess;
    private final CommentService comments;
    private final UserSummaryFacade users;
    private final EventPublisher events;
    private final Clock clock;
    private final String appBaseUrl;

    public InteractionService(CommunityRepository repository, PostAccess postAccess, CommentService comments,
            UserSummaryFacade users, EventPublisher events, Clock clock,
            @Value("${jobforge.auth.app-base-url:http://localhost:3000}") String appBaseUrl) {
        this.repository = repository;
        this.postAccess = postAccess;
        this.comments = comments;
        this.users = users;
        this.events = events;
        this.clock = clock;
        this.appBaseUrl = appBaseUrl.endsWith("/") ? appBaseUrl.substring(0, appBaseUrl.length() - 1) : appBaseUrl;
    }

    // ---- post like / save / share
    @Transactional
    public void likePost(AuthenticatedUser caller, UUID postId) {
        PostRow post = postAccess.requirePublic(postId);
        if (repository.insertPostLike(postId, caller.id(), clock.instant())) {
            repository.addPostCounter(postId, PostCounter.LIKES, 1);
            if (!post.authorId().equals(caller.id())) { // no self-notification
                events.publish(new DomainEvent(EventTopics.COMMUNITY, "PostLiked", "Post", postId, caller.id(),
                        caller.role(), new PostLiked(postId, caller.id(), post.authorId())));
            }
        }
    }

    @Transactional
    public void unlikePost(AuthenticatedUser caller, UUID postId) {
        postAccess.requirePublic(postId);
        if (repository.deletePostLike(postId, caller.id())) {
            repository.addPostCounter(postId, PostCounter.LIKES, -1);
        }
    }

    @Transactional
    public void savePost(AuthenticatedUser caller, UUID postId) {
        postAccess.requirePublic(postId);
        if (repository.insertPostSave(postId, caller.id(), clock.instant())) {
            repository.addPostCounter(postId, PostCounter.SAVES, 1);
        }
    }

    @Transactional
    public void unsavePost(AuthenticatedUser caller, UUID postId) {
        postAccess.requirePublic(postId);
        if (repository.deletePostSave(postId, caller.id())) {
            repository.addPostCounter(postId, PostCounter.SAVES, -1);
        }
    }

    @Transactional
    public ShareAck share(AuthenticatedUser caller, UUID postId, ShareChannel channel) {
        postAccess.requirePublic(postId);
        if (channel == null) {
            throw ValidationFailedException.of("channel", "REQUIRED", "channel is required");
        }
        repository.insertShare(UUID.randomUUID(), postId, caller.id(), channel, clock.instant());
        repository.addPostCounter(postId, PostCounter.SHARES, 1);
        return new ShareAck(appBaseUrl + "/community/posts/" + postId);
    }

    // ---- comment like
    @Transactional
    public void likeComment(AuthenticatedUser caller, UUID commentId) {
        CommentRow comment = requireVisible(commentId);
        if (repository.insertCommentLike(comment.id(), caller.id(), clock.instant())) {
            repository.addCommentLikes(comment.id(), 1);
        }
    }

    @Transactional
    public void unlikeComment(AuthenticatedUser caller, UUID commentId) {
        CommentRow comment = requireVisible(commentId);
        if (repository.deleteCommentLike(comment.id(), caller.id())) {
            repository.addCommentLikes(comment.id(), -1);
        }
    }

    private CommentRow requireVisible(UUID commentId) {
        CommentRow comment = repository.findComment(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("The comment was not found."));
        return comments.requireVisibleComment(comment.id(), comment.postId());
    }

    // ---- follows
    @Transactional
    public void followUser(AuthenticatedUser caller, UUID targetId) {
        if (caller.id().equals(targetId)) {
            throw new BusinessRuleException("You cannot follow yourself.");
        }
        boolean active = users.summary(targetId).map(UserSummaryFacade.UserSummaryView::active).orElse(false);
        if (!active) {
            throw new ResourceNotFoundException("The user was not found.");
        }
        if (repository.insertUserFollow(caller.id(), targetId, clock.instant())) {
            events.publish(new DomainEvent(EventTopics.COMMUNITY, "UserFollowed", "User", targetId, caller.id(),
                    caller.role(), new UserFollowed(caller.id(), targetId)));
        }
    }

    @Transactional
    public void unfollowUser(AuthenticatedUser caller, UUID targetId) {
        repository.deleteUserFollow(caller.id(), targetId);
    }

    @Transactional
    public void followTag(AuthenticatedUser caller, String slug) {
        repository.insertTagFollow(caller.id(), requireTag(slug).id(), clock.instant());
    }

    @Transactional
    public void unfollowTag(AuthenticatedUser caller, String slug) {
        repository.deleteTagFollow(caller.id(), requireTag(slug).id());
    }

    private TagRow requireTag(String slug) {
        String normalized = Tags.normalize(slug);
        return (normalized == null ? java.util.Optional.<TagRow>empty() : repository.findTagBySlug(normalized))
                .orElseThrow(() -> new ResourceNotFoundException("The tag was not found."));
    }

    // ---- tags
    @Transactional(readOnly = true)
    public List<TagView> searchTags(String q) {
        String prefix = q == null ? "" : q.strip().toLowerCase(java.util.Locale.ROOT);
        if (prefix.length() > 30) {
            throw ValidationFailedException.of("q", "SIZE", "must be at most 30 characters");
        }
        return repository.searchTags(prefix, TAG_LIST_LIMIT).stream().map(t -> new TagView(t.slug(), t.name(), null)).toList();
    }

    @Transactional(readOnly = true)
    public List<TagView> trendingTags() {
        Instant since = clock.instant().minus(TRENDING_DAYS, ChronoUnit.DAYS);
        return repository.trendingTags(since, TAG_LIST_LIMIT).stream()
                .map((TrendingTag t) -> new TagView(t.slug(), t.name(), t.postCount())).toList();
    }
}
