package com.jobforge.backend.interview.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
import com.jobforge.backend.interview.domain.InterviewOutcome;
import com.jobforge.backend.interview.domain.InterviewResponse;
import com.jobforge.backend.interview.domain.InterviewStatus;
import com.jobforge.backend.interview.domain.InterviewType;
import com.jobforge.backend.interview.domain.SeekerChoice;
import com.jobforge.backend.job.facade.JobFacade;
import com.jobforge.backend.profile.facade.ProfileFacade;
import com.jobforge.backend.shared.api.PagedResult;
import com.jobforge.backend.shared.audit.AuditAction;
import com.jobforge.backend.shared.audit.AuditEntry;
import com.jobforge.backend.shared.audit.AuditService;
import com.jobforge.backend.shared.domain.UserRole;
import com.jobforge.backend.shared.error.ApiException;
import com.jobforge.backend.shared.error.ErrorCode;
import com.jobforge.backend.shared.error.ForbiddenException;
import com.jobforge.backend.shared.error.ResourceNotFoundException;
import com.jobforge.backend.shared.events.DomainEvent;
import com.jobforge.backend.shared.events.EventPublisher;
import com.jobforge.backend.shared.events.EventTopics;
import com.jobforge.backend.shared.security.AuthenticatedUser;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

/**
 * Docker-free checks of the interview authorization rules (API_CONTRACT §12.7): seeker ownership, recruiter company
 * scope, ADMIN exclusion, IDOR (out-of-scope = 404, never a mutation, audit or event) and invalid transitions (409).
 * The HTTP-level counterpart is {@code InterviewAuthorizationIT}. Not executed in the authoring session.
 */
class InterviewServiceAccessTest {

    private static final Instant NOW = Instant.parse("2026-10-09T10:00:00Z");

    private final InterviewRepository interviews = mock(InterviewRepository.class);
    private final ApplicationFacade applications = mock(ApplicationFacade.class);
    private final InterviewViewAssembler views = mock(InterviewViewAssembler.class);
    private final JobFacade jobs = mock(JobFacade.class);
    private final CompanyAccessFacade companies = mock(CompanyAccessFacade.class);
    private final ProfileFacade profiles = mock(ProfileFacade.class);
    private final AuditService audit = mock(AuditService.class);
    private final EventPublisher events = mock(EventPublisher.class);

    private final InterviewService service = new InterviewService(interviews, applications, views, jobs, companies, profiles,
            audit, events, Clock.fixed(NOW, ZoneOffset.UTC));

    private final UUID applicationId = UUID.randomUUID();
    private final UUID interviewId = UUID.randomUUID();
    private final UUID jobId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();
    private final AuthenticatedUser owner = new AuthenticatedUser(UUID.randomUUID(), UserRole.JOB_SEEKER);
    private final AuthenticatedUser otherSeeker = new AuthenticatedUser(UUID.randomUUID(), UserRole.JOB_SEEKER);
    private final AuthenticatedUser recruiter = new AuthenticatedUser(UUID.randomUUID(), UserRole.RECRUITER);
    private final AuthenticatedUser foreignRecruiter = new AuthenticatedUser(UUID.randomUUID(), UserRole.RECRUITER);
    private final AuthenticatedUser admin = new AuthenticatedUser(UUID.randomUUID(), UserRole.ADMIN);
    private final InterviewContext context = new InterviewContext(applicationId, jobId, companyId, owner.id(), "INTERVIEW", false);
    private final InterviewView view = new InterviewView(interviewId, applicationId, "INTERVIEW", null, null, null, "VIDEO",
            NOW.plusSeconds(3 * 86_400), 45, "Asia/Kolkata", "https://meet.example.test/x", "SCHEDULED", "PENDING", null, null,
            null, NOW, NOW);

    @BeforeEach
    void wire() {
        // The owner and the recruiter of the company are in scope; everyone else is not.
        when(applications.seekerContext(owner, applicationId)).thenReturn(Optional.of(context));
        when(applications.seekerContext(otherSeeker, applicationId)).thenReturn(Optional.empty());
        when(applications.recruiterContext(recruiter, applicationId)).thenReturn(Optional.of(context));
        when(applications.recruiterContext(foreignRecruiter, applicationId)).thenReturn(Optional.empty());
        when(jobs.getLites(any())).thenReturn(Map.of());
        when(views.seekerView(any(), any())).thenReturn(view);
        when(views.recruiterView(any(), any())).thenReturn(view);
    }

    private Interview stored(InterviewStatus status) {
        return new Interview(interviewId, applicationId, recruiter.id(), InterviewType.VIDEO, NOW.plusSeconds(3 * 86_400), 45,
                "Asia/Kolkata", "https://meet.example.test/x", status,
                status == InterviewStatus.CONFIRMED ? InterviewResponse.CONFIRMED : InterviewResponse.PENDING, null, "internal",
                null, NOW.minusSeconds(60), NOW.minusSeconds(60));
    }

    private void interviewExists(InterviewStatus status) {
        when(interviews.findById(interviewId)).thenReturn(Optional.of(stored(status)));
        when(interviews.update(any(), any(), any())).thenReturn(true);
    }

    private void assertNotFound(org.junit.jupiter.api.function.Executable call) {
        assertThatThrownBy(call::execute).isInstanceOf(ResourceNotFoundException.class);
    }

    private void assertNothingWritten() {
        verify(interviews, never()).insert(any(), any());
        verify(interviews, never()).update(any(), any(), any());
        verifyNoInteractions(audit, events);
    }

    // ------------------------------------------------------------------ seeker ownership

    @Test
    void seekerReadsOwnInterviewWithTheSeekerView() {
        interviewExists(InterviewStatus.SCHEDULED);
        assertThat(service.get(owner, interviewId)).isSameAs(view);
        verify(views).seekerView(any(), eq(context));
        verify(views, never()).recruiterView(any(), any());
    }

    @Test
    void anotherSeekerGetsNotFoundForRead() {
        interviewExists(InterviewStatus.SCHEDULED);
        assertNotFound(() -> service.get(otherSeeker, interviewId));
    }

    @Test
    void anotherSeekerCannotRespondAndNothingIsWritten() {
        interviewExists(InterviewStatus.SCHEDULED);
        assertNotFound(() -> service.respond(otherSeeker, interviewId, new Respond(SeekerChoice.CONFIRM, "ok")));
        assertNothingWritten();
    }

    @Test
    void ownerRespondWritesAndPublishesOnlyAnInterviewRespondedEvent() {
        interviewExists(InterviewStatus.SCHEDULED);
        service.respond(owner, interviewId, new Respond(SeekerChoice.CONFIRM, null));
        ArgumentCaptor<Interview> saved = ArgumentCaptor.forClass(Interview.class);
        verify(interviews).update(saved.capture(), eq(InterviewStatus.SCHEDULED), any());
        assertThat(saved.getValue().status()).isEqualTo(InterviewStatus.CONFIRMED);
        assertThat(saved.getValue().seekerResponse()).isEqualTo(InterviewResponse.CONFIRMED);
        ArgumentCaptor<DomainEvent> event = ArgumentCaptor.forClass(DomainEvent.class);
        verify(events).publish(event.capture());
        assertThat(event.getValue().topic()).isEqualTo(EventTopics.INTERVIEWS);
        assertThat(event.getValue().eventType()).isEqualTo("InterviewResponded");
    }

    // ------------------------------------------------------------------ recruiter company scope / IDOR

    @Test
    void recruiterOfTheCompanyReadsWithTheRecruiterView() {
        interviewExists(InterviewStatus.SCHEDULED);
        assertThat(service.get(recruiter, interviewId)).isSameAs(view);
        verify(views).recruiterView(any(), eq(context));
    }

    @Test
    void recruiterOfAnotherCompanyGetsNotFoundEverywhereAndNothingIsWritten() {
        interviewExists(InterviewStatus.SCHEDULED);
        assertNotFound(() -> service.get(foreignRecruiter, interviewId));
        assertNotFound(() -> service.update(foreignRecruiter, interviewId, new Update(null, null, 60, null, null, null)));
        assertNotFound(() -> service.cancel(foreignRecruiter, interviewId, new Cancel("Position on hold")));
        assertNotFound(() -> service.complete(foreignRecruiter, interviewId, new Complete(InterviewOutcome.COMPLETED)));
        assertNothingWritten();
    }

    @Test
    void schedulingForAForeignApplicationIsNotFoundAndNeverTouchesTheApplication() {
        Schedule command = new Schedule(InterviewType.VIDEO, NOW.plusSeconds(86_400), 45, "Asia/Kolkata", "https://x.example.test", null);
        assertNotFound(() -> service.schedule(foreignRecruiter, applicationId, command));
        verify(applications, never()).prepareForInterview(any(), any());
        assertNothingWritten();
    }

    @Test
    void unknownInterviewLooksLikeAForeignOne() {
        when(interviews.findById(any())).thenReturn(Optional.empty());
        UUID unknown = UUID.randomUUID();
        assertNotFound(() -> service.get(owner, unknown));
        assertNotFound(() -> service.get(recruiter, unknown));
        assertNotFound(() -> service.respond(owner, unknown, new Respond(SeekerChoice.DECLINE, null)));
        assertNotFound(() -> service.cancel(recruiter, unknown, new Cancel("x")));
        assertNothingWritten();
    }

    @Test
    void unapprovedRecruiterIsRefusedNotTreatedAsNotFound() {
        interviewExists(InterviewStatus.SCHEDULED);
        when(applications.recruiterContext(foreignRecruiter, applicationId))
                .thenThrow(new ForbiddenException(ErrorCode.RECRUITER_NOT_APPROVED, "not approved"));
        assertThatThrownBy(() -> service.get(foreignRecruiter, interviewId)).isInstanceOf(ForbiddenException.class);
        when(profiles.isRecruiterApproved(foreignRecruiter.id())).thenReturn(false);
        assertThatThrownBy(() -> service.list(foreignRecruiter, new Filter(null, null, null, null), 0, 20))
                .isInstanceOfSatisfying(ForbiddenException.class, e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.RECRUITER_NOT_APPROVED));
    }

    // ------------------------------------------------------------------ admin is not in the contract

    @Test
    void adminIsRefusedByTheServiceToo() {
        interviewExists(InterviewStatus.SCHEDULED);
        assertThatThrownBy(() -> service.get(admin, interviewId)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.list(admin, new Filter(null, null, null, null), 0, 20)).isInstanceOf(ForbiddenException.class);
    }

    // ------------------------------------------------------------------ list scoping

    @Test
    void seekerListIsScopedToOwnApplications() {
        UUID mine = UUID.randomUUID();
        when(applications.applicationIdsOfSeeker(owner.id())).thenReturn(List.of(mine));
        when(interviews.search(any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(PagedResult.empty());
        service.list(owner, new Filter(null, null, null, null), 0, 20);
        ArgumentCaptor<Collection<UUID>> scope = ArgumentCaptor.forClass(Collection.class);
        verify(interviews).search(scope.capture(), any(), any(), any(), anyInt(), anyInt());
        assertThat(scope.getValue()).containsExactly(mine);
    }

    @Test
    void applicationIdFilterOfAnotherSeekerYieldsAnEmptyScopeNotTheirData() {
        when(interviews.search(any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(PagedResult.empty());
        PagedResult<InterviewView> result = service.list(otherSeeker, new Filter(applicationId, null, null, null), 0, 20);
        assertThat(result.items()).isEmpty();
        ArgumentCaptor<Collection<UUID>> scope = ArgumentCaptor.forClass(Collection.class);
        verify(interviews).search(scope.capture(), any(), any(), any(), anyInt(), anyInt());
        assertThat(scope.getValue()).isEmpty();
        verify(applications, never()).applicationIdsOfSeeker(any());
    }

    @Test
    void recruiterListIsScopedToTheirCompanyAndAForeignApplicationIdNarrowsToNothing() {
        when(profiles.isRecruiterApproved(recruiter.id())).thenReturn(true);
        when(profiles.isRecruiterApproved(foreignRecruiter.id())).thenReturn(true);
        when(companies.membershipOf(recruiter.id())).thenReturn(Optional.of(new CompanyAccessFacade.Membership(companyId, "MEMBER")));
        when(companies.membershipOf(foreignRecruiter.id())).thenReturn(Optional.of(new CompanyAccessFacade.Membership(UUID.randomUUID(), "MEMBER")));
        when(applications.applicationIdsOfCompany(companyId)).thenReturn(List.of(applicationId));
        when(interviews.search(any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(PagedResult.empty());

        service.list(recruiter, new Filter(null, null, null, null), 0, 20);
        service.list(foreignRecruiter, new Filter(applicationId, null, null, null), 0, 20);

        ArgumentCaptor<Collection<UUID>> scope = ArgumentCaptor.forClass(Collection.class);
        verify(interviews, org.mockito.Mockito.times(2)).search(scope.capture(), any(), any(), any(), anyInt(), anyInt());
        assertThat(scope.getAllValues().get(0)).containsExactly(applicationId);
        assertThat(scope.getAllValues().get(1)).isEmpty();
    }

    // ------------------------------------------------------------------ invalid transitions (409)

    @ParameterizedTest
    @EnumSource(value = InterviewStatus.class, names = {"COMPLETED", "CANCELLED", "NO_SHOW"})
    void finalInterviewsRejectEveryChange(InterviewStatus status) {
        interviewExists(status);
        assertConflict(() -> service.update(recruiter, interviewId, new Update(null, null, 60, null, null, null)));
        assertConflict(() -> service.cancel(recruiter, interviewId, new Cancel("Position on hold")));
        assertConflict(() -> service.complete(recruiter, interviewId, new Complete(InterviewOutcome.NO_SHOW)));
        assertConflict(() -> service.respond(owner, interviewId, new Respond(SeekerChoice.DECLINE, null)));
        assertNothingWritten();
    }

    @Test
    void declinedInterviewCannotBeAnsweredAgainOrCompleted() {
        interviewExists(InterviewStatus.DECLINED);
        assertConflict(() -> service.respond(owner, interviewId, new Respond(SeekerChoice.CONFIRM, null)));
        assertConflict(() -> service.complete(recruiter, interviewId, new Complete(InterviewOutcome.COMPLETED)));
        assertNothingWritten();
    }

    @Test
    void repeatingTheStoredAnswerIsAnIdempotentNoOp() {
        interviewExists(InterviewStatus.CONFIRMED);
        service.respond(owner, interviewId, new Respond(SeekerChoice.CONFIRM, null));
        assertNothingWritten();
    }

    // ------------------------------------------------------------------ audit + event on schedule / cancel

    @Test
    void schedulingAuditsAndPublishesInterviewScheduledAfterPreparingTheApplication() {
        when(interviews.existsActiveOverlap(any(), any(), any(), any())).thenReturn(false);
        when(applications.prepareForInterview(recruiter, applicationId)).thenReturn(context);
        when(interviews.findById(any())).thenReturn(Optional.of(stored(InterviewStatus.SCHEDULED)));
        service.schedule(recruiter, applicationId, new Schedule(InterviewType.VIDEO, NOW.plusSeconds(2 * 86_400), 45,
                "Asia/Kolkata", "https://meet.example.test/x", "prefers mornings"));

        verify(applications).prepareForInterview(recruiter, applicationId);
        verify(interviews).insert(any(), any());
        ArgumentCaptor<AuditEntry> entry = ArgumentCaptor.forClass(AuditEntry.class);
        verify(audit).record(entry.capture());
        assertThat(entry.getValue().action()).isEqualTo(AuditAction.INTERVIEW_SCHEDULED);
        assertThat(entry.getValue().actorUserId()).isEqualTo(recruiter.id());
        ArgumentCaptor<DomainEvent> event = ArgumentCaptor.forClass(DomainEvent.class);
        verify(events).publish(event.capture());
        assertThat(event.getValue().eventType()).isEqualTo("InterviewScheduled");
        assertThat(event.getValue().payload().toString()).doesNotContain("prefers mornings");
    }

    @Test
    void cancellingAuditsAndPublishesInterviewCancelledWithoutTheReasonInTheEvent() {
        interviewExists(InterviewStatus.CONFIRMED);
        service.cancel(recruiter, interviewId, new Cancel("Position on hold"));
        ArgumentCaptor<AuditEntry> entry = ArgumentCaptor.forClass(AuditEntry.class);
        verify(audit).record(entry.capture());
        assertThat(entry.getValue().action()).isEqualTo(AuditAction.INTERVIEW_CANCELLED);
        ArgumentCaptor<DomainEvent> event = ArgumentCaptor.forClass(DomainEvent.class);
        verify(events).publish(event.capture());
        assertThat(event.getValue().eventType()).isEqualTo("InterviewCancelled");
        assertThat(event.getValue().payload().toString()).doesNotContain("Position on hold");
    }

    private void assertConflict(org.junit.jupiter.api.function.Executable call) {
        assertThatThrownBy(call::execute).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));
    }
}
