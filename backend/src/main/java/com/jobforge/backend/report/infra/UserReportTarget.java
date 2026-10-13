package com.jobforge.backend.report.infra;

import com.jobforge.backend.report.app.ReportTargetPort;
import com.jobforge.backend.report.domain.ModerationAction;
import com.jobforge.backend.report.domain.ReportTargetType;
import com.jobforge.backend.shared.domain.UserStatus;
import com.jobforge.backend.shared.error.BusinessRuleException;
import com.jobforge.backend.user.facade.UserFacade;
import com.jobforge.backend.user.facade.UserModerationFacade;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * USER reports. Only ACTIVE accounts are reportable; deleted/anonymized accounts are treated as missing. The label
 * is the display name only (never e-mail or other PII). SUSPEND_USER delegates to the user module, which enforces its
 * own rules (no self-suspension, administrators cannot be suspended) and writes USER_STATUS_CHANGED.
 */
@Component
public class UserReportTarget implements ReportTargetPort {

    private final UserFacade users;
    private final UserModerationFacade moderation;

    public UserReportTarget(UserFacade users, UserModerationFacade moderation) {
        this.users = users;
        this.moderation = moderation;
    }

    @Override
    public ReportTargetType type() {
        return ReportTargetType.USER;
    }

    @Override
    public Optional<TargetSnapshot> snapshot(UUID targetId) {
        return users.findById(targetId).filter(u -> u.status() != UserStatus.DELETED).map(u -> {
            String name = ((u.firstName() == null ? "" : u.firstName()) + " "
                    + (u.lastName() == null ? "" : u.lastName())).strip();
            return new TargetSnapshot(u.id(), name.isBlank() ? "User" : name, u.status().name(),
                    u.status() == UserStatus.ACTIVE, u.id());
        });
    }

    @Override
    public void apply(ModerationAction action, ModerationContext context) {
        if (action == ModerationAction.SUSPEND_USER) {
            moderation.suspend(context.moderator(), context.targetId(), context.reason());
            return;
        }
        throw new BusinessRuleException(action + " is not supported for USER reports.");
    }
}
