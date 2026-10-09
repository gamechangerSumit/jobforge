package com.jobforge.backend.job.app;

import com.jobforge.backend.company.facade.CompanyAccessFacade;
import com.jobforge.backend.company.facade.CompanyAccessFacade.CompanyInfo;
import com.jobforge.backend.job.domain.Job;
import com.jobforge.backend.job.domain.JobLocation;
import com.jobforge.backend.job.domain.JobSalary;
import com.jobforge.backend.job.facade.JobApplicationLookup;
import com.jobforge.backend.job.facade.JobFacade.Viewer;
import com.jobforge.backend.job.facade.JobViews.JobSummaryView;
import com.jobforge.backend.job.facade.JobViews.LocationView;
import com.jobforge.backend.job.facade.JobViews.SalaryView;
import com.jobforge.backend.profile.facade.SkillCatalogFacade;
import com.jobforge.backend.profile.facade.SkillCatalogFacade.SkillRef;
import com.jobforge.backend.shared.domain.UserRole;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Builds API views: salary visibility, company summaries, skill names, saved/applied flags (batched). */
@Component
public class JobViewAssembler {

    private final CompanyAccessFacade companies;
    private final SkillCatalogFacade skills;
    private final SavedJobRepository saved;
    private final JobApplicationLookup applications;

    public JobViewAssembler(CompanyAccessFacade companies, SkillCatalogFacade skills, SavedJobRepository saved,
            JobApplicationLookup applications) {
        this.companies = companies;
        this.skills = skills;
        this.saved = saved;
        this.applications = applications;
    }

    /** Company the viewer manages (recruiter membership); empty for everybody else. */
    public Optional<UUID> managedCompany(Viewer viewer) {
        if (viewer == null || viewer.userId() == null || viewer.role() != UserRole.RECRUITER) {
            return Optional.empty();
        }
        return companies.membershipOf(viewer.userId()).map(CompanyAccessFacade.Membership::companyId);
    }

    public boolean canManage(Viewer viewer, Job job) {
        if (viewer == null || viewer.role() == null) {
            return false;
        }
        return viewer.role() == UserRole.ADMIN || managedCompany(viewer).filter(job.companyId()::equals).isPresent();
    }

    public List<JobSummaryView> summaries(List<Job> jobs, Viewer viewer) {
        if (jobs.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = jobs.stream().map(Job::id).toList();
        Map<UUID, CompanyInfo> companyInfo = companies.summaries(jobs.stream().map(Job::companyId).distinct().toList());
        Map<UUID, SkillRef> skillInfo = skills.byIds(skillIds(jobs));
        boolean seeker = viewer != null && viewer.role() == UserRole.JOB_SEEKER && viewer.userId() != null;
        Set<UUID> savedIds = seeker ? saved.savedAmong(viewer.userId(), ids) : Set.of();
        Set<UUID> appliedIds = seeker ? applications.appliedJobIds(viewer.userId(), ids) : Set.of();
        Optional<UUID> managed = managedCompany(viewer);
        boolean admin = viewer != null && viewer.role() == UserRole.ADMIN;
        return jobs.stream().map(job -> new JobSummaryView(
                job.id(), job.content().title(), companyInfo.get(job.companyId()), location(job.content().location()),
                job.content().workMode().name(), job.content().employmentType().name(),
                job.content().experienceLevel().name(),
                salary(job, admin || managed.filter(job.companyId()::equals).isPresent()),
                job.content().skills().stream().map(s -> skillInfo.get(s.skillId())).filter(java.util.Objects::nonNull)
                        .map(SkillRef::slug).toList(),
                job.publishedAt(), seeker ? savedIds.contains(job.id()) : null,
                seeker ? appliedIds.contains(job.id()) : null)).toList();
    }

    public JobDetailView detail(Job job, Viewer viewer, boolean manager) {
        Map<UUID, SkillRef> skillInfo = skills.byIds(skillIds(List.of(job)));
        CompanyInfo company = companies.summaries(List.of(job.companyId())).get(job.companyId());
        boolean seeker = viewer != null && viewer.role() == UserRole.JOB_SEEKER && viewer.userId() != null;
        Boolean isSaved = seeker ? !saved.savedAmong(viewer.userId(), List.of(job.id())).isEmpty() : null;
        Boolean isApplied = seeker ? !applications.appliedJobIds(viewer.userId(), List.of(job.id())).isEmpty() : null;
        Long applicationCount = manager ? applications.applicationCount(job.id()) : null;
        List<JobDetailView.SkillOut> skillOut = job.content().skills().stream()
                .filter(s -> skillInfo.containsKey(s.skillId()))
                .map(s -> new JobDetailView.SkillOut(skillInfo.get(s.skillId()).name(),
                        skillInfo.get(s.skillId()).slug(), s.required()))
                .toList();
        var c = job.content();
        return new JobDetailView(job.id(), job.slug(), c.title(), job.status().name(), company, c.description(),
                c.requirements(), c.benefits(), c.employmentType().name(), c.workMode().name(),
                c.experienceLevel().name(), location(c.location()), salary(job, manager), c.salaryVisible(), c.openings(),
                skillOut, job.publishedAt(), c.expiresAt(), job.closedAt(), job.aiGenerated(), job.qualityScore(),
                applicationCount, isSaved, isApplied, job.version(), job.createdAt(), job.updatedAt());
    }

    public Map<UUID, CompanyInfo> companyInfo(Collection<UUID> companyIds) {
        return companies.summaries(companyIds);
    }

    public static LocationView location(JobLocation location) {
        return location == null ? null : new LocationView(location.city(), location.state(), location.country());
    }

    private static SalaryView salary(Job job, boolean owner) {
        JobSalary s = job.content().salary();
        if (s == null || s.allNull() || (!job.content().salaryVisible() && !owner)) {
            return null;
        }
        return new SalaryView(s.min(), s.max(), s.currency(), s.period() == null ? null : s.period().name());
    }

    private static Set<UUID> skillIds(List<Job> jobs) {
        Set<UUID> ids = new HashSet<>();
        jobs.forEach(j -> j.content().skills().forEach(s -> ids.add(s.skillId())));
        return ids;
    }
}
