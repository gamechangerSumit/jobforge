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
 * POST reports (Session 4, decision D1). Reportable only while publicly visible (ACTIVE, author active).
 * The label is the post title/type only, never body text. HIDE_CONTENT -> HIDDEN and REMOVE_CONTENT -> REMOVED, both
 * through the community facade (state change and counters in the resolve transaction). SUSPEND_USER is not a content
 * action: moderators file a USER report for that.
 */
@Component
public class PostReportTarget implements ReportTargetPort {

    private final CommunityFacade community;

    public PostReportTarget(CommunityFacade community) {
        this.community = community;
    }

    @Override
    public ReportTargetType type() {
        return ReportTargetType.POST;
    }

    @Override
    public Optional<TargetSnapshot> snapshot(UUID targetId) {
        return community.postForReport(targetId)
                .map(v -> new TargetSnapshot(v.id(), v.label(), v.status(), v.publiclyVisible(), v.ownerUserId()));
    }

    @Override
    public void apply(ModerationAction action, ModerationContext context) {
        switch (action) {
            case HIDE_CONTENT -> community.hidePost(context.targetId());
            case REMOVE_CONTENT -> community.removePost(context.targetId());
            default -> throw new BusinessRuleException(action + " is not supported for POST reports.");
        }
    }
}
