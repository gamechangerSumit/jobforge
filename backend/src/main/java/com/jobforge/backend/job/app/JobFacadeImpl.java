package com.jobforge.backend.job.app;

import com.jobforge.backend.company.facade.CompanyAccessFacade.CompanyInfo;
import com.jobforge.backend.job.domain.Job;
import com.jobforge.backend.job.facade.JobFacade;
import com.jobforge.backend.job.facade.JobViews.JobAdminView;
import com.jobforge.backend.job.facade.JobViews.JobApplyView;
import com.jobforge.backend.job.facade.JobViews.JobLiteView;
import com.jobforge.backend.job.facade.JobViews.JobReportView;
import com.jobforge.backend.job.facade.JobViews.JobSummaryView;
import com.jobforge.backend.shared.api.PagedResult;
import java.time.Clock;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobFacadeImpl implements JobFacade {

    private final JobRepository jobs;
    private final JobViewAssembler views;
    private final JobModerationService moderation;
    private final Clock clock;

    public JobFacadeImpl(JobRepository jobs, JobViewAssembler views, JobModerationService moderation, Clock clock) {
        this.jobs = jobs;
        this.views = views;
        this.moderation = moderation;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<JobSummaryView> getSummaries(Collection<UUID> ids, Viewer viewer) {
        Map<UUID, Job> byId = new HashMap<>();
        jobs.findByIds(ids).stream().filter(j -> j.isOpenAt(clock.instant())).forEach(j -> byId.put(j.id(), j));
        List<Job> ordered = ids.stream().map(byId::get).filter(java.util.Objects::nonNull).toList();
        return views.summaries(ordered, viewer == null ? Viewer.ANONYMOUS : viewer);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<JobApplyView> getForApply(UUID jobId) {
        return jobs.findById(jobId).map(j -> new JobApplyView(j.id(), j.companyId(), j.content().title(),
                j.status().name(), j.content().expiresAt()));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, JobLiteView> getLites(Collection<UUID> jobIds) {
        List<Job> found = jobs.findByIds(jobIds);
        Map<UUID, CompanyInfo> companies = views.companyInfo(found.stream().map(Job::companyId).distinct().toList());
        Map<UUID, JobLiteView> result = new HashMap<>();
        found.forEach(j -> result.put(j.id(), new JobLiteView(j.id(), j.content().title(), companies.get(j.companyId()),
                JobViewAssembler.location(j.content().location()))));
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> companyIdOf(UUID jobId) {
        return jobs.findById(jobId).map(Job::companyId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<JobReportView> reportView(UUID jobId) {
        return jobs.findById(jobId).map(j -> new JobReportView(j.id(), j.content().title(), j.status().name(),
                j.companyId(), j.createdBy(), j.isOpenAt(clock.instant())));
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> jobIdsOfCompany(UUID companyId) {
        return jobs.findIdsByCompany(companyId);
    }

    @Override
    public PagedResult<JobAdminView> adminSearch(String q, String status, UUID companyId, int page, int size) {
        return moderation.search(q, status, companyId, page, size);
    }

    @Override
    public JobAdminView removeJob(UUID adminUserId, UUID jobId, String reason) {
        return moderation.remove(adminUserId, jobId, reason);
    }

    @Override
    public JobAdminView restoreJob(UUID adminUserId, UUID jobId) {
        return moderation.restore(adminUserId, jobId);
    }
}
