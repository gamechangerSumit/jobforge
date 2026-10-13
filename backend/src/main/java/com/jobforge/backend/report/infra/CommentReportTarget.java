package com.jobforge.backend.report.infra;

import com.jobforge.backend.community.facade.CommunityFacade;
import com.jobforge.backend.report.app.ReportTargetPort;
import com.jobforge.backend.report.domain.ModerationAction;
import com.jobforge.backend.report.domain.ReportTargetType;
import com.jobforge.backend.shared.error.BusinessRuleException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * COMMENT reports (Session 4, decision D1). Reportable only while publicly visible (ACTIVE, author active, parent post public).
 * The label is the post title/type only, never body text. HIDE_CONTENT -> HIDDEN and REMOVE_CONTENT -> REMOVED, both
 * through the community facade (state change and counters in the resolve transaction). SUSPEND_USER is not a content
 * action: moderators file a USER report for that.
 */
@Component
public class CommentReportTarget implements ReportTargetPort {

    private final CommunityFacade community;

    public CommentReportTarget(CommunityFacade community) {
        this.community = community;
    }

    @Override
    public ReportTargetType type() {
        return ReportTargetType.COMMENT;
    }

    @Override
    public Optional<TargetSnapshot> snapshot(UUID targetId) {
        return community.commentForReport(targetId)
                .map(v -> new TargetSnapshot(v.id(), v.label(), v.status(), v.publiclyVisible(), v.ownerUserId()));
    }

    @Override
    public void apply(ModerationAction action, ModerationContext context) {
        switch (action) {
            case HIDE_CONTENT -> community.hideComment(context.targetId());
            case REMOVE_CONTENT -> community.removeComment(context.targetId());
            default -> throw new BusinessRuleException(action + " is not supported for COMMENT reports.");
        }
    }
}
