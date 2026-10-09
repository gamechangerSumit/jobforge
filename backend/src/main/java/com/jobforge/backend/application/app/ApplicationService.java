package com.jobforge.backend.application.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobforge.backend.application.app.ApplicationCommands.Apply;
import com.jobforge.backend.application.app.ApplicationViews.ApplicationView;
import com.jobforge.backend.application.domain.Application;
import com.jobforge.backend.application.domain.ApplicationStatus;
import com.jobforge.backend.application.domain.ApplicationStatusMachine;
import com.jobforge.backend.application.domain.ApplicationStatusMachine.Actor;
import com.jobforge.backend.application.domain.StatusHistoryEntry;
import com.jobforge.backend.application.events.ApplicationEvents;
import com.jobforge.backend.job.facade.JobFacade;
import com.jobforge.backend.job.facade.JobViews.JobApplyView;
import com.jobforge.backend.profile.facade.SeekerDataFacade;
import com.jobforge.backend.profile.facade.SeekerDataFacade.ProfileSnapshot;
import com.jobforge.backend.shared.api.PagedResult;
import com.jobforge.backend.shared.audit.AuditAction;
import com.jobforge.backend.shared.audit.AuditEntry;
import com.jobforge.backend.shared.audit.AuditOutcome;
import com.jobforge.backend.shared.audit.AuditService;
import com.jobforge.backend.shared.domain.UserRole;
import com.jobforge.backend.shared.error.BusinessRuleException;
import com.jobforge.backend.shared.error.ConflictException;
import com.jobforge.backend.shared.error.ErrorCode;
import com.jobforge.backend.shared.error.ForbiddenException;
import com.jobforge.backend.shared.error.ResourceNotFoundException;
import com.jobforge.backend.shared.events.DomainEvent;
import com.jobforge.backend.shared.events.EventPublisher;
import com.jobforge.backend.shared.events.EventTopics;
import com.jobforge.backend.shared.security.AuthenticatedUser;
import com.jobforge.backend.shared.text.MarkdownSanitizer;
import com.jobforge.backend.user.facade.UserFacade;
import com.jobforge.backend.user.facade.UserSearchFacade;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** JS-6 (apply, withdraw, tracking) and RC-3 (pipeline, status, rating). D-17: one application per seeker per job. */
@Service
public class ApplicationService {

    private static final int APPLICANT_SEARCH_LIMIT = 200;

    private final ApplicationRepository applications;
    private final ApplicationAccess access;
    private final ApplicationViewAssembler views;
    private final JobFacade jobs;
    private final SeekerDataFacade seekers;
    private final UserFacade users;
    private final UserSearchFacade userSearch;
    private final AuditService audit;
    private final EventPublisher events;
    private final ObjectMapper mapper;
    private final Clock clock;

    public ApplicationService(ApplicationRepository applications, ApplicationAccess access, ApplicationViewAssembler views,
            JobFacade jobs, SeekerDataFacade seekers, UserFacade users, UserSearchFacade userSearch, AuditService audit,
            EventPublisher events, ObjectMapper mapper, Clock clock) {
        this.applications = applications;
        this.access = access;
        this.views = views;
        this.jobs = jobs;
        this.seekers = seekers;
        this.users = users;
        this.userSearch = userSearch;
        this.audit = audit;
        this.events = events;
        this.mapper = mapper;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ seeker: apply / withdraw / track

    @Transactional
    public ApplicationView submit(AuthenticatedUser seeker, UUID jobId, Apply command) {
        if (!users.findById(seeker.id()).map(u -> u.emailVerified()).orElse(false)) {
            throw new ForbiddenException(ErrorCode.EMAIL_NOT_VERIFIED, "Verify your email address before applying.");
        }
        Instant now = clock.instant();
        JobApplyView job = jobs.getForApply(jobId).orElseThrow(() -> new ResourceNotFoundException("Job not found."));
        requireOpenForApplications(job, now);
        if (!seekers.ownsActiveResume(seeker.id(), command.resumeId())) {
            throw new BusinessRuleException("The selected resume was not found.");
        }
        ProfileSnapshot snapshot = seekers.snapshot(seeker.id()).orElse(null);
        if (snapshot == null || snapshot.headline() == null || snapshot.headline().isBlank()
                || snapshot.skills() == null || snapshot.skills().isEmpty()) {
            throw new BusinessRuleException("Complete your profile (a headline and at least one skill) before applying.");
        }
        String cover = command.coverLetter() == null ? null : MarkdownSanitizer.strip(command.coverLetter());
        Application application = new Application(UUID.randomUUID(), jobId, seeker.id(), command.resumeId(),
                cover == null || cover.isBlank() ? null : cover, ApplicationStatus.SUBMITTED, snapshotJson(snapshot), null,
                now, now, null, 0, now, now);
        if (!applications.insertIfAbsent(application, now)) {
            String message = applications.findByJobAndSeeker(jobId, seeker.id())
                    .filter(a -> a.status() == ApplicationStatus.WITHDRAWN)
                    .map(a -> "You withdrew your application to this job; applying again is not allowed.")
                    .orElse("You have already applied to this job.");
            throw new ConflictException(ErrorCode.DUPLICATE_APPLICATION, message);
        }
        applications.addHistory(new StatusHistoryEntry(UUID.randomUUID(), application.id(), null, ApplicationStatus.SUBMITTED,
                seeker.id(), null, now));
        audit.record(new AuditEntry(AuditAction.APPLICATION_SUBMITTED, "Application", application.id(), seeker.id(),
                seeker.role(), AuditOutcome.SUCCESS, null, Map.of("status", "SUBMITTED", "jobId", jobId.toString()), null));
        events.publish(new DomainEvent(EventTopics.APPLICATIONS, "ApplicationSubmitted", "Application", application.id(),
                seeker.id(), seeker.role(), new ApplicationEvents.ApplicationSubmitted(application.id(), jobId,
                        job.companyId(), seeker.id(), job.title())));
        return views.seekerView(applications.findById(application.id()).orElseThrow(), true);
    }

    @Transactional
    public ApplicationView withdraw(AuthenticatedUser seeker, UUID applicationId) {
        for (int attempt = 0; attempt < 2; attempt++) {
            Application application = access.requireSeekerOwned(seeker, applicationId);
            ApplicationStatusMachine.require(application.status(), ApplicationStatus.WITHDRAWN, Actor.SEEKER);
            Instant now = clock.instant();
            if (applications.updateStatus(applicationId, ApplicationStatus.WITHDRAWN, application.version(), now, true)) {
                recordTransition(application, ApplicationStatus.WITHDRAWN, seeker, null, now, AuditAction.APPLICATION_WITHDRAWN);
                UUID companyId = jobs.companyIdOf(application.jobId()).orElse(null);
                events.publish(new DomainEvent(EventTopics.APPLICATIONS, "ApplicationWithdrawn", "Application", applicationId,
                        seeker.id(), seeker.role(), new ApplicationEvents.ApplicationWithdrawn(applicationId,
                                application.jobId(), companyId, seeker.id())));
                return views.seekerView(applications.findById(applicationId).orElseThrow(), true);
            }
        }
        throw staleVersion();
    }

    @Transactional(readOnly = true)
    public PagedResult<ApplicationView> listForSeeker(AuthenticatedUser seeker, ApplicationStatus status, String orderBy, int page, int size) {
        PagedResult<Application> result = applications.findBySeeker(seeker.id(), status, orderBy, page, size);
        return new PagedResult<>(views.seekerList(result.items()), result.total());
    }

    // ------------------------------------------------------------------ shared read

    @Transactional
    public ApplicationView get(AuthenticatedUser caller, UUID applicationId) {
        if (caller.role() == UserRole.JOB_SEEKER) {
            return views.seekerView(access.requireSeekerOwned(caller, applicationId), true);
        }
        if (caller.role() == UserRole.ADMIN) {
            Application application = applications.findById(applicationId).orElseThrow(ApplicationAccess::notFound);
            audit.record(new AuditEntry(AuditAction.CANDIDATE_PROFILE_VIEWED, "Application", applicationId, caller.id(),
                    caller.role(), AuditOutcome.SUCCESS, null, null, Map.of("seekerUserId", application.seekerUserId().toString())));
            return views.recruiterView(application, true);
        }
        return views.recruiterView(access.requireRecruiterAccess(caller, applicationId), true);
    }

    // ------------------------------------------------------------------ recruiter (RC-3)

    @Transactional(readOnly = true)
    public PagedResult<ApplicationView> listForJob(AuthenticatedUser caller, UUID jobId, ApplicationStatus status, String q,
            String orderBy, int page, int size) {
        if (caller.role() == UserRole.ADMIN) {
            jobs.companyIdOf(jobId).orElseThrow(() -> new ResourceNotFoundException("Job not found."));
        } else {
            access.requireApprovedRecruiter(caller.id());
            access.requireCompanyJob(caller, jobId);
        }
        List<UUID> seekerIds = q == null ? null : userSearch.findSeekerIdsByNameOrHandle(q, APPLICANT_SEARCH_LIMIT);
        PagedResult<Application> result = applications.findByJobs(List.of(jobId), status, seekerIds, orderBy, page, size);
        return new PagedResult<>(views.recruiterList(result.items()), result.total());
    }

    @Transactional(readOnly = true)
    public PagedResult<ApplicationView> listForRecruiter(AuthenticatedUser caller, ApplicationStatus status, UUID jobId,
            String orderBy, int page, int size) {
        access.requireApprovedRecruiter(caller.id());
        Optional<UUID> company = access.companyOf(caller.id());
        if (company.isEmpty()) {
            return PagedResult.empty();
        }
        List<UUID> jobIds = jobs.jobIdsOfCompany(company.get());
        if (jobId != null) {
            jobIds = jobIds.contains(jobId) ? List.of(jobId) : List.of();
        }
        PagedResult<Application> result = applications.findByJobs(jobIds, status, null, orderBy, page, size);
        return new PagedResult<>(views.recruiterList(result.items()), result.total());
    }

    @Transactional
    public ApplicationView changeStatus(AuthenticatedUser recruiter, UUID applicationId, ApplicationStatus target, String reason,
            Long ifMatch) {
        String cleanReason = reason == null || reason.isBlank() ? null : MarkdownSanitizer.strip(reason);
        for (int attempt = 0; attempt < 2; attempt++) {
            Application application = access.requireRecruiterAccess(recruiter, applicationId);
            if (ifMatch != null && ifMatch != application.version()) {
                throw staleVersion();
            }
            ApplicationStatusMachine.require(application.status(), target, Actor.RECRUITER);
            Instant now = clock.instant();
            if (applications.updateStatus(applicationId, target, application.version(), now, false)) {
                recordTransition(application, target, recruiter, cleanReason, now, AuditAction.APPLICATION_STATUS_CHANGED);
                UUID companyId = jobs.companyIdOf(application.jobId()).orElse(null);
                events.publish(new DomainEvent(EventTopics.APPLICATIONS, "ApplicationStatusChanged", "Application", applicationId,
                        recruiter.id(), recruiter.role(), new ApplicationEvents.ApplicationStatusChanged(applicationId,
                                application.jobId(), companyId, application.seekerUserId(), application.status().name(),
                                target.name())));
                return views.recruiterView(applications.findById(applicationId).orElseThrow(), true);
            }
            if (ifMatch != null) {
                break; // the caller pinned a version: do not silently re-evaluate against a newer one
            }
        }
        throw staleVersion();
    }

    @Transactional
    public ApplicationView rate(AuthenticatedUser recruiter, UUID applicationId, int rating) {
        access.requireRecruiterAccess(recruiter, applicationId);
        applications.updateRating(applicationId, rating, clock.instant());
        return views.recruiterView(applications.findById(applicationId).orElseThrow(), true);
    }

    // ------------------------------------------------------------------ internals

    private void requireOpenForApplications(JobApplyView job, Instant now) {
        switch (job.status()) {
            case "PUBLISHED" -> {
                if (job.expiresAt() != null && !job.expiresAt().isAfter(now)) {
                    throw new BusinessRuleException("This job is no longer accepting applications.");
                }
            }
            case "CLOSED", "EXPIRED" -> throw new BusinessRuleException("This job is no longer accepting applications.");
            default -> throw new ResourceNotFoundException("Job not found."); // DRAFT / UNPUBLISHED / REMOVED are not visible
        }
    }

    private void recordTransition(Application application, ApplicationStatus to, AuthenticatedUser actor, String reason,
            Instant now, String auditAction) {
        applications.addHistory(new StatusHistoryEntry(UUID.randomUUID(), application.id(), application.status(), to,
                actor.id(), reason, now));
        audit.record(new AuditEntry(auditAction, "Application", application.id(), actor.id(), actor.role(),
                AuditOutcome.SUCCESS, Map.of("status", application.status().name()), Map.of("status", to.name()), null));
    }

    private String snapshotJson(ProfileSnapshot snapshot) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("headline", snapshot.headline());
        map.put("skills", snapshot.skills());
        map.put("yearsExperience", snapshot.yearsExperience());
        try {
            return mapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Profile snapshot could not be serialized", e);
        }
    }

    private static ConflictException staleVersion() {
        return new ConflictException(ErrorCode.STALE_VERSION, "The application was modified by someone else. Reload and retry.");
    }
}
