package com.jobforge.backend.report.infra;

import com.jobforge.backend.job.facade.JobFacade;
import com.jobforge.backend.report.app.ReportTargetPort;
import com.jobforge.backend.report.domain.ModerationAction;
import com.jobforge.backend.report.domain.ReportTargetType;
import com.jobforge.backend.shared.error.BusinessRuleException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * JOB reports. Reportable only while the job is publicly visible. REMOVE_CONTENT reuses the job module's admin
 * removal (state machine, JOB_REMOVED audit, JobRemoved event). There is no "hidden" job state, so HIDE_CONTENT and
 * SUSPEND_USER are rejected.
 */
@Component
public class JobReportTarget implements ReportTargetPort {

    private final JobFacade jobs;

    public JobReportTarget(JobFacade jobs) {
        this.jobs = jobs;
    }

    @Override
    public ReportTargetType type() {
        return ReportTargetType.JOB;
    }

    @Override
    public Optional<TargetSnapshot> snapshot(UUID targetId) {
        return jobs.reportView(targetId)
                .map(j -> new TargetSnapshot(j.id(), j.title(), j.status(), j.publiclyVisible(), j.createdBy()));
    }

    @Override
    public void apply(ModerationAction action, ModerationContext context) {
        if (action == ModerationAction.REMOVE_CONTENT) {
            jobs.removeJob(context.moderator().id(), context.targetId(), context.reason());
            return;
        }
        throw new BusinessRuleException(action + " is not supported for JOB reports.");
    }
}
