package com.jobforge.backend.interview.app;

import com.jobforge.backend.application.facade.ApplicationFacade;
import com.jobforge.backend.application.facade.ApplicationFacade.InterviewContext;
import com.jobforge.backend.company.facade.CompanyAccessFacade;
import com.jobforge.backend.interview.app.InterviewCommands.Cancel;
import com.jobforge.backend.interview.app.InterviewCommands.Complete;
import com.jobforge.backend.interview.app.InterviewCommands.Filter;
import com.jobforge.backend.interview.app.InterviewCommands.Respond;
import com.jobforge.backend.interview.app.InterviewCommands.Schedule;
import com.jobforge.backend.interview.app.InterviewCommands.Update;
import com.jobforge.backend.interview.app.InterviewViews.InterviewView;
import com.jobforge.backend.interview.domain.Interview;
import com.jobforge.backend.interview.domain.InterviewResponse;
import com.jobforge.backend.interview.domain.InterviewScheduling;
import com.jobforge.backend.interview.domain.InterviewStateMachine;
import com.jobforge.backend.interview.domain.InterviewStatus;
import com.jobforge.backend.interview.domain.InterviewType;
import com.jobforge.backend.interview.events.InterviewEvents;
import com.jobforge.backend.job.facade.JobFacade;
import com.jobforge.backend.job.facade.JobViews.JobLiteView;
import com.jobforge.backend.profile.facade.ProfileFacade;
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
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RC-4 / JS-7 interview scheduling (API_CONTRACT §12.7).
 * <ul>
 *   <li>Recruiters act only on interviews of applications to their own company's jobs (approved recruiters only).</li>
 *   <li>Seekers act only on interviews of their own applications.</li>
 *   <li>Anything outside the caller's scope is "not found" (404), never 403, so ids cannot be probed.</li>
 *   <li>Application status changes go exclusively through {@link ApplicationFacade}.</li>
 * </ul>
 */
@Service
public class InterviewService {

    static final int MAX_NOTES = 2000;
    private static final Pattern UNSAFE_SCHEME = Pattern.compile("^\\s*(javascript|data|vbscript):", Pattern.CASE_INSENSITIVE);

    private final InterviewRepository interviews;
    private final ApplicationFacade applications;
    private final InterviewViewAssembler views;
    private final JobFacade jobs;
    private final CompanyAccessFacade companies;
    private final ProfileFacade profiles;
    private final AuditService audit;
    private final EventPublisher events;
    private final Clock clock;

    public InterviewService(InterviewRepository interviews, ApplicationFacade applications, InterviewViewAssembler views,
            JobFacade jobs, CompanyAccessFacade companies, ProfileFacade profiles, AuditService audit, EventPublisher events,
            Clock clock) {
        this.interviews = interviews;
        this.applications = applications;
        this.views = views;
        this.jobs = jobs;
        this.companies = companies;
        this.profiles = profiles;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ recruiter: schedule / edit / cancel / complete

    @Transactional
    public InterviewView schedule(AuthenticatedUser recruiter, UUID applicationId, Schedule command) {
        Instant now = clock.instant();
        applications.recruiterContext(recruiter, applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found."));
        String timezone = InterviewScheduling.requireTimezone(command.timezone());
        requireDuration(command.durationMinutes());
        String location = cleanLocation(command.locationOrLink());
        InterviewScheduling.requireBookable(command.scheduledAt(), now);
        Instant end = command.scheduledAt().plusSeconds(command.durationMinutes() * 60L);
        if (interviews.existsActiveOverlap(applicationId, command.scheduledAt(), end, null)) {
            throw new ConflictException("This application already has an interview in that time slot.");
        }
        // Moves SHORTLISTED -> INTERVIEW through the application state machine (409 INVALID_STATE_TRANSITION otherwise).
        InterviewContext context = applications.prepareForInterview(recruiter, applicationId);

        Interview interview = new Interview(UUID.randomUUID(), applicationId, recruiter.id(), command.type(),
                command.scheduledAt(), command.durationMinutes(), timezone, location, InterviewStatus.SCHEDULED,
                InterviewResponse.PENDING, null, cleanNotes(command.notes()), null, now, now);
        interviews.insert(interview, now);

        audit.record(new AuditEntry(AuditAction.INTERVIEW_SCHEDULED, "Interview", interview.id(), recruiter.id(), recruiter.role(),
                AuditOutcome.SUCCESS, null,
                Map.of("status", "SCHEDULED", "applicationId", applicationId.toString(), "type", interview.type().name(),
                        "scheduledAt", interview.scheduledAt().toString()),
                null));
        String title = jobTitle(context);
        events.publish(new DomainEvent(EventTopics.INTERVIEWS, "InterviewScheduled", "Interview", interview.id(), recruiter.id(),
                recruiter.role(), new InterviewEvents.InterviewScheduled(interview.id(), applicationId, context.jobId(),
                        context.companyId(), context.seekerUserId(), recruiter.id(), title, interview.type().name(),
                        interview.scheduledAt().toString(), interview.durationMinutes())));
        return views.recruiterView(reload(interview.id()), context);
    }

    @Transactional
    public InterviewView update(AuthenticatedUser recruiter, UUID interviewId, Update command) {
        Interview current = findOrNotFound(interviewId);
        InterviewContext context = recruiterContext(recruiter, current);
        InterviewStateMachine.requireEditable(current.status());
        Instant now = clock.instant();

        InterviewType type = command.type() != null ? command.type() : current.type();
        Instant scheduledAt = command.scheduledAt() != null ? command.scheduledAt() : current.scheduledAt();
        int duration = command.durationMinutes() != null ? command.durationMinutes() : current.durationMinutes();
        String timezone = command.timezone() != null ? InterviewScheduling.requireTimezone(command.timezone()) : current.timezone();
        String location = command.locationOrLink() != null ? cleanLocation(command.locationOrLink()) : current.locationOrLink();
        String notes = command.notes() != null ? cleanNotes(command.notes()) : current.internalNotes();
        requireDuration(duration);

        boolean timeChanged = !scheduledAt.equals(current.scheduledAt()) || duration != current.durationMinutes();
        boolean visibleChange = timeChanged || type != current.type() || !timezone.equals(current.timezone())
                || !Objects.equals(location, current.locationOrLink());

        if (!visibleChange) {
            // Internal notes only: the seeker sees nothing new, so status, response and notifications stay untouched.
            if (Objects.equals(notes, current.internalNotes())) {
                return views.recruiterView(current, context);
            }
            persist(current.withInternalNotes(notes), current.status(), now);
            return views.recruiterView(reload(interviewId), context);
        }

        if (context.closed()) {
            throw new BusinessRuleException("The application is closed, so the interview can no longer be changed. Cancel it instead.");
        }
        if (!scheduledAt.equals(current.scheduledAt())) {
            InterviewScheduling.requireBookable(scheduledAt, now);
        } else {
            InterviewScheduling.requireNotStarted(current, now);
        }
        if (timeChanged && interviews.existsActiveOverlap(current.applicationId(), scheduledAt,
                scheduledAt.plusSeconds(duration * 60L), current.id())) {
            throw new ConflictException("This application already has an interview in that time slot.");
        }

        persist(current.rescheduled(type, scheduledAt, duration, timezone, location, notes), current.status(), now);
        Interview saved = reload(interviewId);
        events.publish(new DomainEvent(EventTopics.INTERVIEWS, "InterviewUpdated", "Interview", saved.id(), recruiter.id(),
                recruiter.role(), new InterviewEvents.InterviewUpdated(saved.id(), saved.applicationId(), context.jobId(),
                        context.companyId(), context.seekerUserId(), saved.scheduledBy(), jobTitle(context), saved.type().name(),
                        saved.scheduledAt().toString(), saved.durationMinutes())));
        return views.recruiterView(saved, context);
    }

    @Transactional
    public InterviewView cancel(AuthenticatedUser recruiter, UUID interviewId, Cancel command) {
        Interview current = findOrNotFound(interviewId);
        InterviewContext context = recruiterContext(recruiter, current);
        InterviewStateMachine.requireCancellable(current.status());
        String reason = MarkdownSanitizer.strip(command.reason());
        if (reason == null || reason.isBlank()) {
            throw ValidationFailedException.of("reason", "NOT_BLANK", "must not be blank");
        }
        persist(current.cancelled(reason), current.status(), clock.instant());
        Interview saved = reload(interviewId);

        audit.record(new AuditEntry(AuditAction.INTERVIEW_CANCELLED, "Interview", saved.id(), recruiter.id(), recruiter.role(),
                AuditOutcome.SUCCESS, Map.of("status", current.status().name()),
                Map.of("status", "CANCELLED", "applicationId", saved.applicationId().toString()), null));
        events.publish(new DomainEvent(EventTopics.INTERVIEWS, "InterviewCancelled", "Interview", saved.id(), recruiter.id(),
                recruiter.role(), new InterviewEvents.InterviewCancelled(saved.id(), saved.applicationId(), context.jobId(),
                        context.companyId(), context.seekerUserId(), saved.scheduledBy(), jobTitle(context), saved.type().name(),
                        saved.scheduledAt().toString(), saved.durationMinutes(), "RECRUITER")));
        return views.recruiterView(saved, context);
    }

    @Transactional
    public InterviewView complete(AuthenticatedUser recruiter, UUID interviewId, Complete command) {
        Interview current = findOrNotFound(interviewId);
        InterviewContext context = recruiterContext(recruiter, current);
        InterviewStatus next = InterviewStateMachine.afterCompletion(current.status(), command.outcome());
        InterviewScheduling.requireStarted(current, clock.instant());
        persist(current.withStatus(next), current.status(), clock.instant());
        return views.recruiterView(reload(interviewId), context);
    }

    // ------------------------------------------------------------------ seeker: respond

    @Transactional
    public InterviewView respond(AuthenticatedUser seeker, UUID interviewId, Respond command) {
        Interview current = findOrNotFound(interviewId);
        InterviewContext context = applications.seekerContext(seeker, current.applicationId())
                .orElseThrow(InterviewService::notFound);
        if (InterviewStateMachine.isRepeatedResponse(current.status(), command.response())) {
            return views.seekerView(current, context); // idempotent replay: no change, no event
        }
        InterviewStatus next = InterviewStateMachine.afterResponse(current.status(), command.response());
        if (context.closed()) {
            throw new BusinessRuleException("This application is closed, so the interview can no longer be answered.");
        }
        Instant now = clock.instant();
        InterviewScheduling.requireNotStarted(current, now);
        String note = command.note() == null ? null : MarkdownSanitizer.strip(command.note());
        persist(current.responded(next, InterviewStateMachine.responseFor(command.response()),
                note == null || note.isBlank() ? null : note), current.status(), now);
        Interview saved = reload(interviewId);
        events.publish(new DomainEvent(EventTopics.INTERVIEWS, "InterviewResponded", "Interview", saved.id(), seeker.id(),
                seeker.role(), new InterviewEvents.InterviewResponded(saved.id(), saved.applicationId(), context.jobId(),
                        context.companyId(), context.seekerUserId(), saved.scheduledBy(), jobTitle(context), saved.type().name(),
                        saved.scheduledAt().toString(), saved.durationMinutes(), saved.seekerResponse().name())));
        return views.seekerView(saved, context);
    }

    // ------------------------------------------------------------------ system: application closed

    /**
     * Cancels every open interview of an application that was withdrawn or rejected (driven by the application events).
     * Idempotent: a second call finds nothing open. Records a system audit entry and an InterviewCancelled event per
     * interview; the reason shown to the seeker is a fixed sentence, never free text.
     */
    @Transactional
    public int cancelOpenInterviewsOfClosedApplication(UUID applicationId, UUID jobId, UUID companyId, UUID seekerUserId,
            String cause) {
        Instant now = clock.instant();
        String reason = "APPLICATION_WITHDRAWN".equals(cause) ? "The candidate withdrew the application."
                : "The application was closed.";
        String title = jobTitle(jobId);
        int cancelled = 0;
        for (Interview open : interviews.findOpenByApplication(applicationId)) {
            if (!interviews.update(open.cancelled(reason), open.status(), now)) {
                continue; // changed concurrently (e.g. a recruiter cancelled it first)
            }
            cancelled++;
            audit.record(new AuditEntry(AuditAction.INTERVIEW_CANCELLED, "Interview", open.id(), null, null, AuditOutcome.SUCCESS,
                    Map.of("status", open.status().name()),
                    Map.of("status", "CANCELLED", "applicationId", applicationId.toString()), Map.of("cause", cause)));
            events.publish(new DomainEvent(EventTopics.INTERVIEWS, "InterviewCancelled", "Interview", open.id(), null, null,
                    new InterviewEvents.InterviewCancelled(open.id(), applicationId, jobId, companyId, seekerUserId,
                            open.scheduledBy(), title, open.type().name(), open.scheduledAt().toString(),
                            open.durationMinutes(), cause)));
        }
        return cancelled;
    }

    // ------------------------------------------------------------------ reads

    @Transactional(readOnly = true)
    public InterviewView get(AuthenticatedUser caller, UUID interviewId) {
        Interview interview = findOrNotFound(interviewId);
        if (caller.role() == UserRole.JOB_SEEKER) {
            InterviewContext context = applications.seekerContext(caller, interview.applicationId())
                    .orElseThrow(InterviewService::notFound);
            return views.seekerView(interview, context);
        }
        if (caller.role() == UserRole.RECRUITER) {
            return views.recruiterView(interview, recruiterContext(caller, interview));
        }
        throw new ForbiddenException(ErrorCode.ACCESS_DENIED, "You do not have access to interviews.");
    }

    @Transactional(readOnly = true)
    public PagedResult<InterviewView> list(AuthenticatedUser caller, Filter filter, int page, int size) {
        if (filter.from() != null && filter.to() != null && filter.from().isAfter(filter.to())) {
            throw ValidationFailedException.of("from", "MAX", "must not be after 'to'");
        }
        if (caller.role() == UserRole.JOB_SEEKER) {
            List<UUID> applicationIds = filter.applicationId() == null ? applications.applicationIdsOfSeeker(caller.id())
                    : applications.seekerContext(caller, filter.applicationId()).map(c -> List.of(c.applicationId())).orElse(List.of());
            PagedResult<Interview> result = interviews.search(applicationIds, filter.status(), filter.from(), filter.to(), page, size);
            return new PagedResult<>(views.seekerList(result.items(), applications.contexts(applicationIdsOf(result))), result.total());
        }
        if (caller.role() == UserRole.RECRUITER) {
            if (!profiles.isRecruiterApproved(caller.id())) {
                throw new ForbiddenException(ErrorCode.RECRUITER_NOT_APPROVED, "Your recruiter account is not approved yet.");
            }
            Optional<UUID> company = companies.membershipOf(caller.id()).map(CompanyAccessFacade.Membership::companyId);
            if (company.isEmpty()) {
                return PagedResult.empty();
            }
            List<UUID> applicationIds = filter.applicationId() == null ? applications.applicationIdsOfCompany(company.get())
                    : applications.recruiterContext(caller, filter.applicationId()).map(c -> List.of(c.applicationId())).orElse(List.of());
            PagedResult<Interview> result = interviews.search(applicationIds, filter.status(), filter.from(), filter.to(), page, size);
            return new PagedResult<>(views.recruiterList(result.items(), applications.contexts(applicationIdsOf(result))), result.total());
        }
        throw new ForbiddenException(ErrorCode.ACCESS_DENIED, "You do not have access to interviews.");
    }

    // ------------------------------------------------------------------ internals

    private InterviewContext recruiterContext(AuthenticatedUser recruiter, Interview interview) {
        return applications.recruiterContext(recruiter, interview.applicationId()).orElseThrow(InterviewService::notFound);
    }

    private Interview findOrNotFound(UUID id) {
        return interviews.findById(id).orElseThrow(InterviewService::notFound);
    }

    private Interview reload(UUID id) {
        return interviews.findById(id).orElseThrow(InterviewService::notFound);
    }

    private void persist(Interview next, InterviewStatus expected, Instant now) {
        if (!interviews.update(next, expected, now)) {
            throw new ConflictException(ErrorCode.STALE_VERSION, "The interview was modified by someone else. Reload and retry.");
        }
    }

    private String jobTitle(InterviewContext context) {
        return jobTitle(context.jobId());
    }

    private String jobTitle(UUID jobId) {
        JobLiteView lite = jobs.getLites(List.of(jobId)).get(jobId);
        return lite == null ? null : lite.title();
    }

    private static List<UUID> applicationIdsOf(PagedResult<Interview> result) {
        return result.items().stream().map(Interview::applicationId).distinct().toList();
    }

    private static void requireDuration(int minutes) {
        if (minutes < InterviewScheduling.MIN_DURATION_MINUTES || minutes > InterviewScheduling.MAX_DURATION_MINUTES) {
            throw ValidationFailedException.of("durationMinutes", "MIN",
                    "must be between " + InterviewScheduling.MIN_DURATION_MINUTES + " and " + InterviewScheduling.MAX_DURATION_MINUTES);
        }
    }

    /** HTML tags stripped (API_CONTRACT §10); scripting URL schemes are rejected outright. */
    private static String cleanLocation(String value) {
        String clean = value == null ? null : MarkdownSanitizer.strip(value);
        if (clean == null || clean.isBlank()) {
            throw ValidationFailedException.of("locationOrLink", "NOT_BLANK", "must not be blank");
        }
        if (UNSAFE_SCHEME.matcher(clean).find()) {
            throw new ValidationFailedException(List.of(new FieldErrorDetail("locationOrLink", "PATTERN",
                    "must be an address or an http(s) link")));
        }
        if (clean.length() > 500) {
            throw ValidationFailedException.of("locationOrLink", "SIZE", "must be at most 500 characters");
        }
        return clean;
    }

    /** Blank clears (merge-patch precedent, CHANGELOG_CONTRACTS 2026-10-09). */
    private static String cleanNotes(String value) {
        if (value == null) {
            return null;
        }
        String clean = MarkdownSanitizer.strip(value);
        if (clean != null && clean.length() > MAX_NOTES) {
            throw ValidationFailedException.of("notes", "SIZE", "must be at most " + MAX_NOTES + " characters");
        }
        return clean == null || clean.isBlank() ? null : clean;
    }

    private static ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("Interview not found.");
    }
}
