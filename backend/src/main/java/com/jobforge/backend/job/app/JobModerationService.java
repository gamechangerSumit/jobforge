package com.jobforge.backend.job.app;

import com.jobforge.backend.company.facade.CompanyAccessFacade.CompanyInfo;
import com.jobforge.backend.job.domain.Job;
import com.jobforge.backend.job.domain.JobStatus;
import com.jobforge.backend.job.domain.JobStatusMachine;
import com.jobforge.backend.job.domain.JobStatusMachine.Actor;
import com.jobforge.backend.job.events.JobEvents;
import com.jobforge.backend.job.facade.JobViews.JobAdminView;
import com.jobforge.backend.shared.api.PagedResult;
import com.jobforge.backend.shared.audit.AuditAction;
import com.jobforge.backend.shared.audit.AuditEntry;
import com.jobforge.backend.shared.audit.AuditOutcome;
import com.jobforge.backend.shared.audit.AuditService;
import com.jobforge.backend.shared.domain.UserRole;
import com.jobforge.backend.shared.error.ConflictException;
import com.jobforge.backend.shared.error.ErrorCode;
import com.jobforge.backend.shared.error.ResourceNotFoundException;
import com.jobforge.backend.shared.error.ValidationFailedException;
import com.jobforge.backend.shared.events.DomainEvent;
import com.jobforge.backend.shared.events.EventPublisher;
import com.jobforge.backend.shared.events.EventTopics;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** AD-2: job moderation (remove / restore / search). Callers are authorized as ADMIN at the API edge. */
@Service
public class JobModerationService {

    private final JobRepository jobs;
    private final JobViewAssembler views;
    private final AuditService audit;
    private final EventPublisher events;
    private final Clock clock;

    public JobModerationService(JobRepository jobs, JobViewAssembler views, AuditService audit, EventPublisher events, Clock clock) {
        this.jobs = jobs;
        this.views = views;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PagedResult<JobAdminView> search(String q, String status, UUID companyId, int page, int size) {
        JobStatus parsed = null;
        if (status != null) {
            try {
                parsed = JobStatus.valueOf(status);
            } catch (IllegalArgumentException e) {
                throw ValidationFailedException.of("status", "INVALID_ENUM", "must be one of the allowed values");
            }
        }
        PagedResult<Job> result = jobs.adminSearch(parsed, q, companyId, page, size);
        Map<UUID, CompanyInfo> companies = views.companyInfo(result.items().stream().map(Job::companyId).distinct().toList());
        return new PagedResult<>(result.items().stream().map(j -> toView(j, companies.get(j.companyId()))).toList(), result.total());
    }

    @Transactional
    public JobAdminView remove(UUID adminUserId, UUID jobId, String reason) {
        Job job = jobs.findById(jobId).orElseThrow(() -> new ResourceNotFoundException("Job not found."));
        JobStatusMachine.require(job.status(), JobStatus.REMOVED, Actor.ADMIN);
        persist(job.withRemoval(JobStatus.REMOVED, adminUserId, reason), job);
        audit.record(new AuditEntry(AuditAction.JOB_REMOVED, "Job", jobId, adminUserId, UserRole.ADMIN, AuditOutcome.SUCCESS,
                Map.of("status", job.status().name()), Map.of("status", "REMOVED"), Map.of("reason", reason)));
        events.publish(new DomainEvent(EventTopics.JOBS, "JobRemoved", "Job", jobId, adminUserId, UserRole.ADMIN,
                new JobEvents.JobRemoved(jobId, job.companyId())));
        return reload(jobId);
    }

    @Transactional
    public JobAdminView restore(UUID adminUserId, UUID jobId) {
        Job job = jobs.findById(jobId).orElseThrow(() -> new ResourceNotFoundException("Job not found."));
        JobStatusMachine.require(job.status(), JobStatus.UNPUBLISHED, Actor.ADMIN);
        persist(job.withRemoval(JobStatus.UNPUBLISHED, null, null), job);
        audit.record(new AuditEntry(AuditAction.JOB_RESTORED, "Job", jobId, adminUserId, UserRole.ADMIN, AuditOutcome.SUCCESS,
                Map.of("status", "REMOVED"), Map.of("status", "UNPUBLISHED"), null));
        return reload(jobId);
    }

    private void persist(Job updated, Job before) {
        if (!jobs.update(updated, before.version(), clock.instant(), false)) {
            throw new ConflictException(ErrorCode.STALE_VERSION, "The job was modified concurrently. Reload and retry.");
        }
    }

    private JobAdminView reload(UUID jobId) {
        Job job = jobs.findById(jobId).orElseThrow();
        return toView(job, views.companyInfo(List.of(job.companyId())).get(job.companyId()));
    }

    private static JobAdminView toView(Job j, CompanyInfo company) {
        return new JobAdminView(j.id(), j.content().title(), j.slug(), j.status().name(), company, j.createdBy(),
                j.publishedAt(), j.content().expiresAt(), j.removedReason(), j.version(), j.createdAt(), j.updatedAt());
    }
}
