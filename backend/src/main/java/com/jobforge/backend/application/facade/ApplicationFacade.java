package com.jobforge.backend.application.facade;

import com.jobforge.backend.shared.security.AuthenticatedUser;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Public API of the application module for other modules (job counts, search flags and the interview module). */
public interface ApplicationFacade {

    long countByJob(UUID jobId);

    /** True when the seeker has a non-withdrawn application to any of the given jobs. */
    boolean hasActiveApplicationTo(UUID seekerUserId, Collection<UUID> jobIds);

    /**
     * What the interview module may know about an application. {@code status} is the application status name;
     * {@code closed} is true for the terminal statuses HIRED, REJECTED and WITHDRAWN.
     */
    record InterviewContext(UUID applicationId, UUID jobId, UUID companyId, UUID seekerUserId, String status, boolean closed) {}

    /**
     * Context of an application of the recruiter's own company; empty when the application does not exist or belongs
     * to another company (callers answer 404).
     *
     * @throws com.jobforge.backend.shared.error.ForbiddenException RECRUITER_NOT_APPROVED
     */
    Optional<InterviewContext> recruiterContext(AuthenticatedUser recruiter, UUID applicationId);

    /** Context of an application owned by the seeker; empty when missing or owned by someone else. */
    Optional<InterviewContext> seekerContext(AuthenticatedUser seeker, UUID applicationId);

    /** Batched, unauthorised lookup used to render interview lists whose ids were already authorised. */
    Map<UUID, InterviewContext> contexts(Collection<UUID> applicationIds);

    /**
     * Prepares an application for an interview using the existing status machine: SHORTLISTED moves to INTERVIEW
     * (history, audit and ApplicationStatusChanged event included), INTERVIEW stays as is.
     *
     * @throws com.jobforge.backend.shared.error.ResourceNotFoundException when the recruiter has no access
     * @throws com.jobforge.backend.shared.error.ConflictException INVALID_STATE_TRANSITION from any other status
     */
    InterviewContext prepareForInterview(AuthenticatedUser recruiter, UUID applicationId);

    /** Ids of every application to a job of the company (used to scope interview lists). */
    List<UUID> applicationIdsOfCompany(UUID companyId);

    /** Ids of every application of the seeker (used to scope interview lists). */
    List<UUID> applicationIdsOfSeeker(UUID seekerUserId);
}
