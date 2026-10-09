package com.jobforge.backend.application.app;

import com.jobforge.backend.application.domain.Application;
import com.jobforge.backend.company.facade.CompanyAccessFacade;
import com.jobforge.backend.job.facade.JobFacade;
import com.jobforge.backend.profile.facade.ProfileFacade;
import com.jobforge.backend.shared.error.ErrorCode;
import com.jobforge.backend.shared.error.ForbiddenException;
import com.jobforge.backend.shared.error.ResourceNotFoundException;
import com.jobforge.backend.shared.security.AuthenticatedUser;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Ownership policy for applications: a seeker owns the application; a recruiter may act only on applications to jobs
 * of their own company. Anything else is "not found". Recruiter access to candidate data also requires an APPROVED
 * recruiter (restrictive default, see REQ-20261005).
 */
@Component
public class ApplicationAccess {

    private final ApplicationRepository applications;
    private final JobFacade jobs;
    private final CompanyAccessFacade companies;
    private final ProfileFacade profiles;

    public ApplicationAccess(ApplicationRepository applications, JobFacade jobs, CompanyAccessFacade companies, ProfileFacade profiles) {
        this.applications = applications;
        this.jobs = jobs;
        this.companies = companies;
        this.profiles = profiles;
    }

    public void requireApprovedRecruiter(UUID userId) {
        if (!profiles.isRecruiterApproved(userId)) {
            throw new ForbiddenException(ErrorCode.RECRUITER_NOT_APPROVED, "Your recruiter account is not approved yet.");
        }
    }

    public Optional<UUID> companyOf(UUID userId) {
        return companies.membershipOf(userId).map(CompanyAccessFacade.Membership::companyId);
    }

    public Application requireSeekerOwned(AuthenticatedUser caller, UUID applicationId) {
        return applications.findById(applicationId)
                .filter(a -> a.seekerUserId().equals(caller.id()))
                .orElseThrow(ApplicationAccess::notFound);
    }

    public Application requireRecruiterAccess(AuthenticatedUser caller, UUID applicationId) {
        requireApprovedRecruiter(caller.id());
        Application application = applications.findById(applicationId).orElseThrow(ApplicationAccess::notFound);
        requireCompanyJob(caller, application.jobId());
        return application;
    }

    /** The job must belong to the recruiter's company, otherwise 404. */
    public UUID requireCompanyJob(AuthenticatedUser caller, UUID jobId) {
        UUID company = companyOf(caller.id()).orElseThrow(ApplicationAccess::notFound);
        UUID jobCompany = jobs.companyIdOf(jobId).orElseThrow(ApplicationAccess::notFound);
        if (!company.equals(jobCompany)) {
            throw notFound();
        }
        return company;
    }

    public static ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("Application not found.");
    }
}
