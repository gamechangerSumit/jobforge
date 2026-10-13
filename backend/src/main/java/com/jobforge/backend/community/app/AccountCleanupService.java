package com.jobforge.backend.community.app;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reacts to a deleted account (event {@code UserDeleted}, DATABASE_SCHEMA 11): hard-deletes its likes, saves, follows
 * and received mentions, fixes the affected counters and recomputes the comment counters of the posts it commented on
 * (its comments stop being visible with the account). Idempotent: running it twice changes nothing.
 */
@Service
public class AccountCleanupService {

    private final CommunityRepository repository;

    public AccountCleanupService(CommunityRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void purge(UUID userId) {
        List<UUID> commentedPosts = repository.postIdsCommentedBy(userId);
        repository.purgeInteractionsOf(userId);
        commentedPosts.forEach(repository::recountComments);
    }
}
