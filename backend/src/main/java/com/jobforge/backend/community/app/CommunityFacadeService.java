package com.jobforge.backend.community.app;

import com.jobforge.backend.community.app.CommunityRecords.CommentRow;
import com.jobforge.backend.community.app.CommunityRecords.PostRow;
import com.jobforge.backend.community.domain.ModerationState;
import com.jobforge.backend.community.facade.CommunityFacade;
import com.jobforge.backend.shared.error.BusinessRuleException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Facade implementation: report snapshots and moderation state changes (decision D1: HIDE -> HIDDEN, REMOVE -> REMOVED). */
@Service
public class CommunityFacadeService implements CommunityFacade {

    private final CommunityRepository repository;
    private final PostAccess postAccess;

    public CommunityFacadeService(CommunityRepository repository, PostAccess postAccess) {
        this.repository = repository;
        this.postAccess = postAccess;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ReportView> postForReport(UUID postId) {
        return repository.findPost(postId).map(p -> new ReportView(p.id(), postLabel(p), p.status().name(),
                p.authorId(), postAccess.isPublic(p)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ReportView> commentForReport(UUID commentId) {
        return repository.findComment(commentId).filter(c -> c.deletedAt() == null).map(c -> {
            Optional<PostRow> post = repository.findPost(c.postId());
            String label = "Comment on " + post.map(CommunityFacadeService::postLabel).orElse("a removed post");
            boolean parentVisible = c.parentId() == null || repository.findComment(c.parentId())
                    .filter(pc -> pc.deletedAt() == null && pc.status() == ModerationState.ACTIVE
                            && postAccess.authorActive(pc.authorId()))
                    .isPresent(); // a reply is hidden together with its parent
            boolean visible = c.status() == ModerationState.ACTIVE && postAccess.authorActive(c.authorId())
                    && parentVisible && post.map(postAccess::isPublic).orElse(false);
            return new ReportView(c.id(), label, c.status().name(), c.authorId(), visible);
        });
    }

    @Override
    @Transactional
    public void hidePost(UUID postId) {
        if (!repository.transitionPost(postId, ModerationState.ACTIVE, ModerationState.HIDDEN)) {
            throw new BusinessRuleException("Only an active post can be hidden.");
        }
    }

    @Override
    @Transactional
    public void removePost(UUID postId) {
        if (!repository.transitionPost(postId, ModerationState.ACTIVE, ModerationState.REMOVED)
                && !repository.transitionPost(postId, ModerationState.HIDDEN, ModerationState.REMOVED)) {
            throw new BusinessRuleException("The post is already removed.");
        }
    }

    @Override
    @Transactional
    public void hideComment(UUID commentId) {
        CommentRow c = requireComment(commentId);
        if (!repository.transitionComment(commentId, ModerationState.ACTIVE, ModerationState.HIDDEN)) {
            throw new BusinessRuleException("Only an active comment can be hidden.");
        }
        repository.recountComments(c.postId()); // also drops the replies hidden together with this comment
    }

    @Override
    @Transactional
    public void removeComment(UUID commentId) {
        CommentRow c = requireComment(commentId);
        if (!repository.transitionComment(commentId, ModerationState.ACTIVE, ModerationState.REMOVED)
                && !repository.transitionComment(commentId, ModerationState.HIDDEN, ModerationState.REMOVED)) {
            throw new BusinessRuleException("The comment is already removed.");
        }
        repository.recountComments(c.postId()); // also drops the replies hidden together with this comment
    }

    private CommentRow requireComment(UUID id) {
        return repository.findComment(id).filter(c -> c.deletedAt() == null)
                .orElseThrow(() -> new BusinessRuleException("The comment no longer exists. Dismiss the report instead."));
    }

    private static String postLabel(PostRow p) {
        return p.title() != null && !p.title().isBlank() ? p.title() : "Post (" + p.type().name() + ")";
    }
}
