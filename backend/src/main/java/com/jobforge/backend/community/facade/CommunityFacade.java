package com.jobforge.backend.community.facade;

import java.util.Optional;
import java.util.UUID;

/**
 * Public API of the community module for the report/moderation module (ARCHITECTURE 4 rule 2). Moderation methods run
 * inside the caller's transaction and reject invalid state transitions with 422 BUSINESS_RULE_VIOLATED.
 */
public interface CommunityFacade {

    /**
     * @param label            short admin label (post title or type, never body text)
     * @param status           ACTIVE | HIDDEN | REMOVED
     * @param publiclyVisible  ordinary users can see it now (ACTIVE, author active, parent post public for comments)
     */
    record ReportView(UUID id, String label, String status, UUID ownerUserId, boolean publiclyVisible) {}

    /** Empty when missing or soft-deleted. */
    Optional<ReportView> postForReport(UUID postId);

    Optional<ReportView> commentForReport(UUID commentId);

    void hidePost(UUID postId);

    void removePost(UUID postId);

    void hideComment(UUID commentId);

    void removeComment(UUID commentId);
}
