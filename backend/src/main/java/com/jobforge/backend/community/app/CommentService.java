package com.jobforge.backend.community.app;

import com.jobforge.backend.community.app.CommunityEvents.CommentCreated;
import com.jobforge.backend.community.app.CommunityRecords.CommentRow;
import com.jobforge.backend.community.app.CommunityRecords.CursorPos;
import com.jobforge.backend.community.app.CommunityRecords.PostRow;
import com.jobforge.backend.community.app.CommunityViews.CommentView;
import com.jobforge.backend.community.domain.CommunityLimits;
import com.jobforge.backend.community.domain.ModerationState;
import com.jobforge.backend.shared.api.CursorMeta;
import com.jobforge.backend.shared.api.CursorResponse;
import com.jobforge.backend.shared.audit.AuditAction;
import com.jobforge.backend.shared.audit.AuditEntry;
import com.jobforge.backend.shared.audit.AuditOutcome;
import com.jobforge.backend.shared.audit.AuditService;
import com.jobforge.backend.shared.domain.UserRole;
import com.jobforge.backend.shared.error.BusinessRuleException;
import com.jobforge.backend.shared.error.ForbiddenException;
import com.jobforge.backend.shared.error.ResourceNotFoundException;
import com.jobforge.backend.shared.events.DomainEvent;
import com.jobforge.backend.shared.events.EventPublisher;
import com.jobforge.backend.shared.events.EventTopics;
import com.jobforge.backend.shared.security.AuthenticatedUser;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Comments (API_CONTRACT 12.9): top-level list with {@code parentId} for replies, max depth 2. posts.comment_count counts
 * visible (ACTIVE, not deleted) comments including replies; it moves in the same transaction as the comment row.
 */
@Service
public class CommentService {

    private static final String NOT_FOUND = "The comment was not found.";

    private final CommunityRepository repository;
    private final ViewAssembler views;
    private final PostAccess postAccess;
    private final PostService posts;
    private final AuditService audit;
    private final EventPublisher events;
    private final CursorCodec cursors;
    private final Clock clock;
    private final SpamGuard spam;

    public CommentService(CommunityRepository repository, ViewAssembler views, PostAccess postAccess, PostService posts,
            AuditService audit, EventPublisher events, CursorCodec cursors, Clock clock, SpamGuard spam) {
        this.spam = spam;
        this.repository = repository;
        this.views = views;
        this.postAccess = postAccess;
        this.posts = posts;
        this.audit = audit;
        this.events = events;
        this.cursors = cursors;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public CursorResponse<CommentView> list(AuthenticatedUser caller, UUID postId, UUID parentId, String cursor, int limit) {
        postAccess.requirePublic(postId);
        if (parentId != null) {
            requireVisibleComment(parentId, postId);
        }
        String kind = "comments:" + postId + ":" + (parentId == null ? "top" : parentId);
        CursorPos after = cursors.decode(kind, cursor);
        List<CommentRow> rows = repository.comments(postId, parentId, after, limit + 1);
        boolean hasNext = rows.size() > limit;
        List<CommentRow> page = hasNext ? rows.subList(0, limit) : rows;
        String next = hasNext ? cursors.encode(kind,
                new CursorPos(page.get(page.size() - 1).createdAt(), null, page.get(page.size() - 1).id())) : null;
        return new CursorResponse<>(views.comments(caller.id(), page), new CursorMeta(next, hasNext, limit));
    }

    @Transactional
    public CommentView create(AuthenticatedUser caller, UUID postId, String rawBody, UUID parentId) {
        PostRow post = postAccess.requirePublic(postId);
        String body = CommunityText.body(rawBody, CommunityLimits.COMMENT_MAX, "body");
        CommentRow parent = null;
        if (parentId != null) {
            parent = requireVisibleComment(parentId, postId);
            if (parent.parentId() != null) {
                throw new BusinessRuleException("Replies can only be nested " + CommunityLimits.COMMENT_MAX_DEPTH + " levels deep.");
            }
        }
        spam.checkNewComment(caller, body);
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        repository.insertComment(id, postId, caller.id(), parentId, body, now);
        repository.recountComments(postId);
        posts.syncMentions("COMMENT", id, postId, body, caller, now);
        events.publish(new DomainEvent(EventTopics.COMMUNITY, "CommentCreated", "Comment", id, caller.id(), caller.role(),
                new CommentCreated(id, postId, caller.id(), parentId, post.authorId(), parent == null ? null : parent.authorId())));
        return views.comments(caller.id(), List.of(repository.findComment(id).orElseThrow())).get(0);
    }

    @Transactional
    public CommentView edit(AuthenticatedUser caller, UUID id, String rawBody) {
        CommentRow comment = repository.findCommentForUpdate(id).orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        requireVisibleComment(comment.id(), comment.postId());
        if (!comment.authorId().equals(caller.id())) {
            throw new ForbiddenException("Only the author can edit this comment.");
        }
        String body = CommunityText.body(rawBody, CommunityLimits.COMMENT_MAX, "body");
        spam.checkEditedComment(caller, body);
        repository.updateComment(id, body, clock.instant());
        posts.syncMentions("COMMENT", id, comment.postId(), body, caller, clock.instant());
        return views.comments(caller.id(), List.of(repository.findComment(id).orElseThrow())).get(0);
    }

    @Transactional
    public void delete(AuthenticatedUser caller, UUID id) {
        CommentRow comment = repository.findCommentForUpdate(id).orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        boolean author = comment.authorId().equals(caller.id());
        boolean admin = caller.role() == UserRole.ADMIN;
        if (!author && !admin) {
            // visible to others -> 403; hidden/removed/unreachable -> 404 (no probing)
            requireVisibleComment(comment.id(), comment.postId());
            throw new ForbiddenException("Only the author or an administrator can delete this comment.");
        }
        if (!repository.softDeleteComment(id, clock.instant())) {
            throw new ResourceNotFoundException(NOT_FOUND);
        }
        // exact recount: also drops the replies of a deleted top-level comment (they are hidden with it)
        repository.recountComments(comment.postId());
        if (admin && !author) { // decision D2
            audit.record(new AuditEntry(AuditAction.CONTENT_MODERATED, "Comment", id, caller.id(), caller.role(),
                    AuditOutcome.SUCCESS, null, null,
                    Map.of("action", "ADMIN_DELETE", "authorId", comment.authorId().toString())));
        }
    }

    /**
     * Comment must be ACTIVE, not deleted, by an active author and belong to {@code postId}; a reply additionally needs
     * a visible parent (hiding, removing or deleting a top-level comment hides its replies with it); the post must be
     * public. Anything else is 404.
     */
    CommentRow requireVisibleComment(UUID commentId, UUID postId) {
        CommentRow comment = repository.findComment(commentId).orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        requireVisible(comment, postId);
        if (comment.parentId() != null) {
            CommentRow parent = repository.findComment(comment.parentId()).orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
            requireVisible(parent, postId);
        }
        postAccess.requirePublic(postId);
        return comment;
    }

    private void requireVisible(CommentRow comment, UUID postId) {
        if (!comment.postId().equals(postId) || comment.deletedAt() != null || comment.status() != ModerationState.ACTIVE
                || !postAccess.authorActive(comment.authorId())) {
            throw new ResourceNotFoundException(NOT_FOUND);
        }
    }
}
