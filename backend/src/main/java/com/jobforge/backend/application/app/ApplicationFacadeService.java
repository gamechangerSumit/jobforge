package com.jobforge.backend.application.app;

import com.jobforge.backend.application.domain.Application;
import com.jobforge.backend.application.domain.ApplicationStatus;
import com.jobforge.backend.application.facade.ApplicationFacade;
import com.jobforge.backend.job.facade.JobFacade;
import com.jobforge.backend.shared.error.ConflictException;
import com.jobforge.backend.shared.error.ErrorCode;
import com.jobforge.backend.shared.security.AuthenticatedUser;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link ApplicationFacade}. All status moves go through {@link ApplicationService#changeStatus} so
 * the transition matrix, history, audit and ApplicationStatusChanged event stay owned by this module.
 */
@Service
public class ApplicationFacadeService implements ApplicationFacade {

    private final ApplicationRepository applications;
    private final ApplicationAccess access;
    private final ApplicationService service;
    private final JobFacade jobs;

    public ApplicationFacadeService(ApplicationRepository applications, ApplicationAccess access, ApplicationService service,
            JobFacade jobs) {
        this.applications = applications;
        this.access = access;
        this.service = service;
        this.jobs = jobs;
    }

    @Override
    @Transactional(readOnly = true)
    public long countByJob(UUID jobId) {
        return applications.countByJob(jobId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasActiveApplicationTo(UUID seekerUserId, Collection<UUID> jobIds) {
        return applications.existsActive(seekerUserId, jobIds);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<InterviewContext> recruiterContext(AuthenticatedUser recruiter, UUID applicationId) {
        access.requireApprovedRecruiter(recruiter.id());
        Optional<UUID> company = access.companyOf(recruiter.id());
        if (company.isEmpty()) {
            return Optional.empty();
        }
        return applications.findById(applicationId)
                .map(this::toContext)
                .filter(ctx -> company.get().equals(ctx.companyId()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<InterviewContext> seekerContext(AuthenticatedUser seeker, UUID applicationId) {
        return applications.findById(applicationId)
                .filter(a -> a.seekerUserId().equals(seeker.id()))
                .map(this::toContext);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, InterviewContext> contexts(Collection<UUID> applicationIds) {
        Map<UUID, InterviewContext> result = new HashMap<>();
        applications.findAllByIds(applicationIds).forEach(a -> result.put(a.id(), toContext(a)));
        return result;
    }

    @Override
    @Transactional
    public InterviewContext prepareForInterview(AuthenticatedUser recruiter, UUID applicationId) {
        Application application = access.requireRecruiterAccess(recruiter, applicationId);
        if (application.status() == ApplicationStatus.INTERVIEW) {
            return toContext(application);
        }
        if (application.status() != ApplicationStatus.SHORTLISTED) {
            throw new ConflictException(ErrorCode.INVALID_STATE_TRANSITION,
                    "An interview can only be scheduled for a shortlisted candidate (application status is "
                            + application.status() + ").");
        }
        service.changeStatus(recruiter, applicationId, ApplicationStatus.INTERVIEW, null, null);
        return toContext(applications.findById(applicationId).orElseThrow(ApplicationAccess::notFound));
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> applicationIdsOfCompany(UUID companyId) {
        return applications.idsByJobs(jobs.jobIdsOfCompany(companyId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> applicationIdsOfSeeker(UUID seekerUserId) {
        return applications.idsBySeeker(seekerUserId);
    }

    private InterviewContext toContext(Application a) {
        UUID companyId = jobs.companyIdOf(a.jobId()).orElse(null);
        return new InterviewContext(a.id(), a.jobId(), companyId, a.seekerUserId(), a.status().name(), a.status().isTerminal());
    }
}
