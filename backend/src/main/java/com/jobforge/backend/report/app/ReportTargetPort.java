package com.jobforge.backend.report.app;

import com.jobforge.backend.report.domain.ModerationAction;
import com.jobforge.backend.report.domain.ReportTargetType;
import com.jobforge.backend.shared.error.BusinessRuleException;
import com.jobforge.backend.shared.security.AuthenticatedUser;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapter contract between the report module and the module owning a reportable resource. Implementations live in
 * {@code report.infra} and talk to the owning module through its facade only (ARCHITECTURE rule 2). The community
 * module registers POST/COMMENT ports later; until then those target types cannot be reported.
 */
public interface ReportTargetPort {

    /**
     * @param label     short display text for ADMINS only (job title, company name, user name); never body content
     * @param reportable whether an ordinary user may see (and therefore report) the resource right now
     * @param ownerUserId the user responsible for the resource, or null when unknown
     */
    record TargetSnapshot(UUID id, String label, String status, boolean reportable, UUID ownerUserId) {}

    record ModerationContext(AuthenticatedUser moderator, UUID targetId, String reason) {}

    ReportTargetType type();

    /** Empty when the resource does not exist or is soft-deleted. Callers decide what a non-reportable snapshot means. */
    Optional<TargetSnapshot> snapshot(UUID targetId);

    /**
     * Executes a content/account action on the target. Only actions that exist for the resource type in the contracts
     * are implemented; everything else is rejected with 422 instead of inventing behavior.
     */
    default void apply(ModerationAction action, ModerationContext context) {
        throw new BusinessRuleException(action + " is not supported for " + type() + " reports.");
    }
}
