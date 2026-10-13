package com.jobforge.backend.user.facade;

import com.jobforge.backend.shared.security.AuthenticatedUser;
import java.util.UUID;

/**
 * Account actions the report module may trigger as the outcome of a moderation decision. The user module keeps all
 * rules (no self-change, administrators cannot be suspended, only ACTIVE/SUSPENDED accounts) and its own audit trail.
 */
public interface UserModerationFacade {

    /**
     * Suspends the account.
     *
     * @throws com.jobforge.backend.shared.error.ResourceNotFoundException unknown user
     * @throws com.jobforge.backend.shared.error.BusinessRuleException self, administrator or non-active/suspended account
     */
    void suspend(AuthenticatedUser moderator, UUID userId, String reason);
}
