package com.jobforge.backend.community.app;

import com.jobforge.backend.community.app.CommunityRecords.PostRow;
import com.jobforge.backend.community.domain.ModerationState;
import com.jobforge.backend.shared.domain.UserRole;
import com.jobforge.backend.shared.error.ResourceNotFoundException;
import com.jobforge.backend.shared.security.AuthenticatedUser;
import com.jobforge.backend.user.facade.UserSummaryFacade;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Visibility rules (Session 4 decision D8). A post is PUBLIC when it is ACTIVE, not soft-deleted and its author
 * account is ACTIVE. The author and ADMIN may additionally read their non-public (HIDDEN/REMOVED) posts. Everything
 * else is indistinguishable from "missing": 404 (no ID probing).
 */
@Component
public class PostAccess {

    static final String NOT_FOUND = "The post was not found.";

    private final CommunityRepository repository;
    private final UserSummaryFacade users;

    public PostAccess(CommunityRepository repository, UserSummaryFacade users) {
        this.repository = repository;
        this.users = users;
    }

    public boolean isPublic(PostRow post) {
        return post.deletedAt() == null && post.status() == ModerationState.ACTIVE && authorActive(post.authorId());
    }

    public PostRow requireReadable(PostRow post, AuthenticatedUser caller) {
        if (post.deletedAt() != null) {
            throw new ResourceNotFoundException(NOT_FOUND);
        }
        if (post.authorId().equals(caller.id()) || caller.role() == UserRole.ADMIN || isPublic(post)) {
            return post;
        }
        throw new ResourceNotFoundException(NOT_FOUND);
    }

    public PostRow requirePublic(UUID postId) {
        PostRow post = repository.findPost(postId).orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        if (!isPublic(post)) {
            throw new ResourceNotFoundException(NOT_FOUND);
        }
        return post;
    }

    boolean authorActive(UUID authorId) {
        return users.summary(authorId).map(UserSummaryFacade.UserSummaryView::active).orElse(false);
    }
}
