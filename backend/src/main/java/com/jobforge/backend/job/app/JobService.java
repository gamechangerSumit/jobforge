package com.jobforge.backend.job.app;

import com.jobforge.backend.company.facade.CompanyAccessFacade;
import com.jobforge.backend.job.domain.Job;
import com.jobforge.backend.job.domain.JobContent;
import com.jobforge.backend.job.domain.JobSalary;
import com.jobforge.backend.job.domain.JobSkill;
import com.jobforge.backend.job.domain.JobStatus;
import com.jobforge.backend.job.domain.JobStatusMachine;
import com.jobforge.backend.job.domain.JobStatusMachine.Actor;
import com.jobforge.backend.job.events.JobEvents;
import com.jobforge.backend.job.facade.JobApplicationLookup;
import com.jobforge.backend.job.facade.JobFacade.Viewer;
import com.jobforge.backend.profile.facade.ProfileFacade;
import com.jobforge.backend.profile.facade.SkillCatalogFacade;
import com.jobforge.backend.profile.facade.SkillCatalogFacade.SkillRef;
import com.jobforge.backend.shared.api.PagedResult;
import com.jobforge.backend.shared.audit.AuditAction;
import com.jobforge.backend.shared.audit.AuditEntry;
import com.jobforge.backend.shared.audit.AuditOutcome;
import com.jobforge.backend.shared.audit.AuditService;
import com.jobforge.backend.shared.domain.UserRole;
import com.jobforge.backend.shared.error.BusinessRuleException;
import com.jobforge.backend.shared.error.ConflictException;
import com.jobforge.backend.shared.error.ErrorCode;
import com.jobforge.backend.shared.error.FieldErrorDetail;
import com.jobforge.backend.shared.error.ForbiddenException;
import com.jobforge.backend.shared.error.ResourceNotFoundException;
import com.jobforge.backend.shared.error.ValidationFailedException;
import com.jobforge.backend.shared.events.DomainEvent;
import com.jobforge.backend.shared.events.EventPublisher;
import com.jobforge.backend.shared.events.EventTopics;
import com.jobforge.backend.shared.security.AuthenticatedUser;
import com.jobforge.backend.shared.text.MarkdownSanitizer;
import com.jobforge.backend.user.facade.UserFacade;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recruiter job use cases (RC-2): create, edit, publish, unpublish, close, delete, detail, company job list.
 * D-16 is enforced on every transition to PUBLISHED (publish and re-open).
 */
@Service
public class JobService {

    private static final Duration DEFAULT_EXPIRY = Duration.ofDays(30);

    private final JobRepository jobs;
    private final CompanyAccessFacade companies;
    private final SkillCatalogFacade skills;
    private final ProfileFacade profiles;
    private final UserFacade users;
    private final JobApplicationLookup applications;
    private final JobViewAssembler views;
    private final AuditService audit;
    private final EventPublisher events;
    private final Clock clock;

    public JobService(JobRepository jobs, CompanyAccessFacade companies, SkillCatalogFacade skills, ProfileFacade profiles,
            UserFacade users, JobApplicationLookup applications, JobViewAssembler views, AuditService audit,
            EventPublisher events, Clock clock) {
        this.jobs = jobs;
        this.companies = companies;
        this.skills = skills;
        this.profiles = profiles;
        this.users = users;
        this.applications = applications;
        this.views = views;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ queries

    @Transactional(readOnly = true)
    public JobDetailView getDetail(UUID id, Viewer viewer) {
        Job job = jobs.findById(id).orElseThrow(JobService::notFound);
        boolean manager = views.canManage(viewer, job);
        if (!manager && !job.isOpenAt(clock.instant())) {
            throw notFound();
        }
        return views.detail(job, viewer, manager);
    }

    @Transactional(readOnly = true)
    public PagedResult<RecruiterJobItem> listCompanyJobs(AuthenticatedUser caller, JobStatus status, String q, int page, int size) {
        var membership = companies.membershipOf(caller.id());
        if (membership.isEmpty()) {
            return PagedResult.empty();
        }
        PagedResult<Job> result = jobs.findByCompany(membership.get().companyId(), status, q, page, size);
        List<RecruiterJobItem> items = result.items().stream().map(j -> new RecruiterJobItem(j.id(), j.content().title(),
                j.slug(), j.status().name(), j.content().workMode().name(), j.content().employmentType().name(),
                JobViewAssembler.location(j.content().location()), j.publishedAt(), j.content().expiresAt(),
                applications.applicationCount(j.id()), j.version(), j.updatedAt())).toList();
        return new PagedResult<>(items, result.total());
    }

    // ------------------------------------------------------------------ commands

    @Transactional
    public JobDetailView create(AuthenticatedUser caller, JobDraft draft) {
        UUID companyId = companies.membershipOf(caller.id())
                .orElseThrow(() -> new ForbiddenException("You must belong to a company to post jobs."))
                .companyId();
        if (draft.aiRequestId() != null) {
            throw new BusinessRuleException("AI-generated drafts are not available yet.");
        }
        Instant now = clock.instant();
        JobContent content = buildContent(draft, resolveSkills(draft.skills()));
        validateSalary(content.salary());
        Job job = new Job(UUID.randomUUID(), companyId, caller.id(), Slugs.forTitle(content.title()), content,
                JobStatus.DRAFT, null, null, false, null, null, null, null, 0, now, now);
        jobs.insert(job, now);
        audit.record(new AuditEntry(AuditAction.JOB_CREATED, "Job", job.id(), caller.id(), caller.role(),
                AuditOutcome.SUCCESS, null, Map.of("status", "DRAFT", "companyId", companyId.toString()), null));
        return views.detail(jobs.findById(job.id()).orElseThrow(), asViewer(caller), true);
    }

    @Transactional
    public JobDetailView update(AuthenticatedUser caller, UUID id, JobDraft patch, Long ifMatch) {
        Job job = loadManageable(caller, id);
        requireNotRemoved(job);
        if (ifMatch != null && ifMatch != job.version()) {
            throw staleVersion();
        }
        boolean skillsChanged = patch.has("skills");
        List<JobSkill> resolved = skillsChanged ? resolveSkills(requireNonNull("skills", patch.skills())) : job.content().skills();
        JobContent merged = merge(job.content(), patch, resolved);
        validateSalary(merged.salary());
        Instant now = clock.instant();
        if (job.status() == JobStatus.PUBLISHED) {
            JobPublishValidator.require(merged, now); // a live job must stay publishable
        }
        List<String> changed = changedFields(job.content(), merged);
        Job updated = job.withContent(merged);
        if (!jobs.update(updated, job.version(), now, skillsChanged)) {
            throw staleVersion();
        }
        audit.record(new AuditEntry(AuditAction.JOB_UPDATED, "Job", id, caller.id(), caller.role(), AuditOutcome.SUCCESS,
                null, null, Map.of("changedFields", changed)));
        if (job.status() == JobStatus.PUBLISHED && !changed.isEmpty()) {
            publish(caller, "JobUpdated", id, new JobEvents.JobUpdated(id, job.companyId(), changed));
        }
        return views.detail(jobs.findById(id).orElseThrow(), asViewer(caller), true);
    }

    @Transactional
    public JobDetailView publish(AuthenticatedUser caller, UUID id) {
        return publish(caller, id, null);
    }

    /** {@code ifMatch} (optional) must equal the current job version, else STALE_VERSION. */
    @Transactional
    public JobDetailView publish(AuthenticatedUser caller, UUID id, Long ifMatch) {
        return transition(caller, id, JobStatus.PUBLISHED, ifMatch);
    }

    @Transactional
    public JobDetailView unpublish(AuthenticatedUser caller, UUID id) {
        return unpublish(caller, id, null);
    }

    /** {@code ifMatch} (optional) must equal the current job version, else STALE_VERSION. */
    @Transactional
    public JobDetailView unpublish(AuthenticatedUser caller, UUID id, Long ifMatch) {
        return transition(caller, id, JobStatus.UNPUBLISHED, ifMatch);
    }

    @Transactional
    public JobDetailView close(AuthenticatedUser caller, UUID id) {
        return close(caller, id, null);
    }

    /** {@code ifMatch} (optional) must equal the current job version, else STALE_VERSION. */
    @Transactional
    public JobDetailView close(AuthenticatedUser caller, UUID id, Long ifMatch) {
        return transition(caller, id, JobStatus.CLOSED, ifMatch);
    }

    @Transactional
    public void delete(AuthenticatedUser caller, UUID id) {
        Job job = loadManageable(caller, id);
        requireNotRemoved(job);
        if (applications.applicationCount(id) > 0) {
            throw new BusinessRuleException("Jobs with applications cannot be deleted. Close the job instead.");
        }
        if (!jobs.softDelete(id, job.version(), clock.instant())) {
            throw staleVersion();
        }
        audit.record(new AuditEntry(AuditAction.JOB_DELETED, "Job", id, caller.id(), caller.role(), AuditOutcome.SUCCESS,
                Map.of("status", job.status().name()), null, null));
    }

    /** Expires due jobs (system actor). Invoked by a scheduler once Dev 3 provides ShedLock; safe to call repeatedly. */
    @Transactional
    public int expireDue() {
        var expired = jobs.expireDue(clock.instant());
        for (var job : expired) {
            events.publish(new DomainEvent(EventTopics.JOBS, "JobExpired", "Job", job.id(), null, null,
                    new JobEvents.JobExpired(job.id(), job.companyId())));
        }
        return expired.size();
    }

    // ------------------------------------------------------------------ internals

    private JobDetailView transition(AuthenticatedUser caller, UUID id, JobStatus target, Long ifMatch) {
        Job job = loadManageable(caller, id);
        requireNotRemoved(job);
        if (ifMatch != null && ifMatch != job.version()) {
            throw staleVersion();
        }
        JobStatusMachine.require(job.status(), target, Actor.RECRUITER);
        Instant now = clock.instant();
        Job updated;
        if (target == JobStatus.PUBLISHED) {
            requirePublishPreconditions(caller, job);
            JobContent content = job.content();
            Instant expires = content.expiresAt();
            boolean reopen = job.status() == JobStatus.CLOSED || job.status() == JobStatus.EXPIRED;
            if (expires == null || (reopen && !expires.isAfter(now))) {
                expires = now.plus(DEFAULT_EXPIRY);
            }
            JobContent publishable = content.withExpiresAt(expires);
            JobPublishValidator.require(publishable, now);
            updated = job.withContent(publishable).withStatus(JobStatus.PUBLISHED, now, null);
        } else if (target == JobStatus.CLOSED) {
            updated = job.withStatus(JobStatus.CLOSED, job.publishedAt(), now);
        } else {
            updated = job.withStatus(target, job.publishedAt(), job.closedAt());
        }
        if (!jobs.update(updated, job.version(), now, false)) {
            throw staleVersion();
        }
        String action = switch (target) {
            case PUBLISHED -> AuditAction.JOB_PUBLISHED;
            case UNPUBLISHED -> AuditAction.JOB_UNPUBLISHED;
            default -> AuditAction.JOB_CLOSED;
        };
        audit.record(new AuditEntry(action, "Job", id, caller.id(), caller.role(), AuditOutcome.SUCCESS,
                Map.of("status", job.status().name()), Map.of("status", target.name()), null));
        Object payload = switch (target) {
            case PUBLISHED -> new JobEvents.JobPublished(id, job.companyId(), updated.content().title(), job.slug(), now,
                    updated.content().expiresAt());
            case UNPUBLISHED -> new JobEvents.JobUnpublished(id, job.companyId());
            default -> new JobEvents.JobClosed(id, job.companyId());
        };
        publish(caller, switch (target) {
            case PUBLISHED -> "JobPublished";
            case UNPUBLISHED -> "JobUnpublished";
            default -> "JobClosed";
        }, id, payload);
        return views.detail(jobs.findById(id).orElseThrow(), asViewer(caller), true);
    }

    /** D-16: approved recruiter AND verified company; plus verified e-mail (restrictive default, see REQ). */
    private void requirePublishPreconditions(AuthenticatedUser caller, Job job) {
        boolean emailVerified = users.findById(caller.id()).map(u -> u.emailVerified()).orElse(false);
        if (!emailVerified) {
            throw new ForbiddenException(ErrorCode.EMAIL_NOT_VERIFIED, "Verify your email address before publishing jobs.");
        }
        if (!profiles.isRecruiterApproved(caller.id())) {
            throw new ForbiddenException(ErrorCode.RECRUITER_NOT_APPROVED, "Your recruiter account is not approved yet.");
        }
        if (!companies.isVerified(job.companyId())) {
            throw new ForbiddenException(ErrorCode.COMPANY_NOT_VERIFIED, "Your company is not verified yet.");
        }
    }

    /** Ownership: the caller must belong to the job's company; anything else is "not found". */
    private Job loadManageable(AuthenticatedUser caller, UUID id) {
        Job job = jobs.findById(id).orElseThrow(JobService::notFound);
        boolean member = companies.membershipOf(caller.id()).filter(m -> m.companyId().equals(job.companyId())).isPresent();
        if (!member) {
            throw notFound();
        }
        return job;
    }

    private static void requireNotRemoved(Job job) {
        if (job.status() == JobStatus.REMOVED) {
            throw new BusinessRuleException("This job was removed by moderation and cannot be changed.");
        }
    }

    private List<JobSkill> resolveSkills(List<JobDraft.SkillInput> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            return List.of();
        }
        Map<String, Boolean> byName = new LinkedHashMap<>();
        for (JobDraft.SkillInput input : inputs) {
            String name = input.name().trim().replaceAll("\\s+", " ");
            byName.merge(name.toLowerCase(Locale.ROOT), input.required(), (a, b) -> a || b);
        }
        Map<String, SkillRef> refs = skills.resolveOrCreate(inputs.stream().map(JobDraft.SkillInput::name).toList());
        List<JobSkill> result = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        byName.forEach((key, required) -> {
            SkillRef ref = refs.get(key);
            if (ref != null && seen.add(ref.id())) {
                result.add(new JobSkill(ref.id(), required));
            }
        });
        if (result.size() > 20) {
            throw ValidationFailedException.of("skills", "SIZE", "at most 20 skills are allowed");
        }
        return result;
    }

    private static JobContent buildContent(JobDraft d, List<JobSkill> skills) {
        String description = MarkdownSanitizer.strip(d.description());
        if (description == null || description.length() < 50) {
            throw ValidationFailedException.of("description", "SIZE", "must be at least 50 characters of text");
        }
        return new JobContent(MarkdownSanitizer.strip(d.title()), description, blankToNull(MarkdownSanitizer.strip(d.requirements())),
                blankToNull(MarkdownSanitizer.strip(d.benefits())), d.employmentType(), d.workMode(), d.experienceLevel(),
                d.location() == null || d.location().allNull() ? null : d.location(),
                d.salary() == null || d.salary().allNull() ? null : d.salary(),
                d.salaryVisible() == null || d.salaryVisible(), d.openings() == null ? 1 : d.openings(), d.expiresAt(), skills);
    }

    /** JSON-merge-patch: only fields in {@code present} change; null clears nullable fields. */
    private static JobContent merge(JobContent c, JobDraft p, List<JobSkill> skills) {
        String title = p.has("title") ? requireNonNull("title", MarkdownSanitizer.strip(p.title())) : c.title();
        String description = p.has("description") ? requireNonNull("description", MarkdownSanitizer.strip(p.description())) : c.description();
        if (description.length() < 50) {
            throw ValidationFailedException.of("description", "SIZE", "must be at least 50 characters of text");
        }
        return new JobContent(title, description,
                p.has("requirements") ? blankToNull(MarkdownSanitizer.strip(p.requirements())) : c.requirements(),
                p.has("benefits") ? blankToNull(MarkdownSanitizer.strip(p.benefits())) : c.benefits(),
                p.has("employmentType") ? requireNonNull("employmentType", p.employmentType()) : c.employmentType(),
                p.has("workMode") ? requireNonNull("workMode", p.workMode()) : c.workMode(),
                p.has("experienceLevel") ? requireNonNull("experienceLevel", p.experienceLevel()) : c.experienceLevel(),
                p.has("location") ? (p.location() == null || p.location().allNull() ? null : p.location()) : c.location(),
                p.has("salary") ? (p.salary() == null || p.salary().allNull() ? null : p.salary()) : c.salary(),
                p.has("salaryVisible") ? requireNonNull("salaryVisible", p.salaryVisible()) : c.salaryVisible(),
                p.has("openings") ? requireNonNull("openings", p.openings()) : c.openings(),
                p.has("expiresAt") ? p.expiresAt() : c.expiresAt(), skills);
    }

    private static <T> T requireNonNull(String field, T value) {
        if (value == null) {
            throw ValidationFailedException.of(field, "NOT_BLANK", "must not be null");
        }
        return value;
    }

    private static void validateSalary(JobSalary s) {
        if (s == null) {
            return;
        }
        List<FieldErrorDetail> problems = new ArrayList<>();
        if (s.min() != null && s.max() != null && s.max() < s.min()) {
            problems.add(new FieldErrorDetail("salary.max", "MIN", "must be greater than or equal to min"));
        }
        if ((s.min() != null || s.max() != null) && s.currency() == null) {
            problems.add(new FieldErrorDetail("salary.currency", "NOT_BLANK", "is required when a salary range is set"));
        }
        if ((s.min() != null || s.max() != null) && s.period() == null) {
            problems.add(new FieldErrorDetail("salary.period", "NOT_BLANK", "is required when a salary range is set"));
        }
        if (!problems.isEmpty()) {
            throw new ValidationFailedException(problems);
        }
    }

    private static List<String> changedFields(JobContent a, JobContent b) {
        List<String> changed = new ArrayList<>();
        check(changed, "title", a.title(), b.title());
        check(changed, "description", a.description(), b.description());
        check(changed, "requirements", a.requirements(), b.requirements());
        check(changed, "benefits", a.benefits(), b.benefits());
        check(changed, "employmentType", a.employmentType(), b.employmentType());
        check(changed, "workMode", a.workMode(), b.workMode());
        check(changed, "experienceLevel", a.experienceLevel(), b.experienceLevel());
        check(changed, "location", a.location(), b.location());
        check(changed, "salary", a.salary(), b.salary());
        check(changed, "salaryVisible", a.salaryVisible(), b.salaryVisible());
        check(changed, "openings", a.openings(), b.openings());
        check(changed, "expiresAt", a.expiresAt(), b.expiresAt());
        check(changed, "skills", new HashSet<>(a.skills()), new HashSet<>(b.skills()));
        return changed;
    }

    private static void check(List<String> changed, String name, Object before, Object after) {
        if (!Objects.equals(before, after)) {
            changed.add(name);
        }
    }

    private void publish(AuthenticatedUser caller, String type, UUID jobId, Object payload) {
        events.publish(new DomainEvent(EventTopics.JOBS, type, "Job", jobId, caller.id(), caller.role(), payload));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static Viewer asViewer(AuthenticatedUser caller) {
        return new Viewer(caller.id(), caller.role());
    }

    private static ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("Job not found.");
    }

    private static ConflictException staleVersion() {
        return new ConflictException(ErrorCode.STALE_VERSION, "The job was modified by someone else. Reload and retry.");
    }
}
