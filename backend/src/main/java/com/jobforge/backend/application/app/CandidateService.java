package com.jobforge.backend.application.app;

import com.jobforge.backend.application.app.ApplicationViews.CandidateView;
import com.jobforge.backend.job.facade.JobFacade;
import com.jobforge.backend.profile.facade.SeekerDataFacade;
import com.jobforge.backend.profile.facade.SeekerDataFacade.CandidateProfile;
import com.jobforge.backend.shared.audit.AuditAction;
import com.jobforge.backend.shared.audit.AuditEntry;
import com.jobforge.backend.shared.audit.AuditOutcome;
import com.jobforge.backend.shared.audit.AuditService;
import com.jobforge.backend.shared.domain.UserRole;
import com.jobforge.backend.shared.domain.UserStatus;
import com.jobforge.backend.shared.error.ResourceNotFoundException;
import com.jobforge.backend.shared.security.AuthenticatedUser;
import com.jobforge.backend.user.facade.UserAccountView;
import com.jobforge.backend.user.facade.UserFacade;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code GET /seekers/{userId}} (RC-3 candidate profile access). A recruiter may view a candidate who applied (and has
 * not withdrawn) to a job of their company, or whose profile visibility is not PRIVATE. Admins may always view.
 * Every view is audited. Not visible → 404.
 */
@Service
public class CandidateService {

    private final UserFacade users;
    private final SeekerDataFacade seekers;
    private final ApplicationAccess access;
    private final ApplicationRepository applications;
    private final JobFacade jobs;
    private final AuditService audit;

    public CandidateService(UserFacade users, SeekerDataFacade seekers, ApplicationAccess access,
            ApplicationRepository applications, JobFacade jobs, AuditService audit) {
        this.users = users;
        this.seekers = seekers;
        this.access = access;
        this.applications = applications;
        this.jobs = jobs;
        this.audit = audit;
    }

    @Transactional
    public CandidateView view(AuthenticatedUser caller, UUID seekerUserId) {
        UserAccountView user = users.findById(seekerUserId)
                .filter(u -> u.role() == UserRole.JOB_SEEKER && u.status() != UserStatus.DELETED && u.status() != UserStatus.SUSPENDED)
                .orElseThrow(CandidateService::notFound);
        CandidateProfile profile = seekers.candidate(seekerUserId).orElseThrow(CandidateService::notFound);
        boolean applicant = false;
        if (caller.role() == UserRole.RECRUITER) {
            access.requireApprovedRecruiter(caller.id());
            UUID company = access.companyOf(caller.id()).orElseThrow(CandidateService::notFound);
            applicant = applications.existsActive(seekerUserId, jobs.jobIdsOfCompany(company));
            if (!applicant && "PRIVATE".equals(profile.visibility())) {
                throw notFound();
            }
        }
        audit.record(new AuditEntry(AuditAction.CANDIDATE_PROFILE_VIEWED, "User", seekerUserId, caller.id(), caller.role(),
                AuditOutcome.SUCCESS, null, null, Map.of("applicant", applicant)));
        return new CandidateView(ApplicationViewAssembler.toSummary(user), profile.id(), profile.headline(), profile.summary(),
                profile.location(), profile.currentTitle(), profile.yearsExperience(), profile.noticePeriodDays(),
                profile.openToWork(), profile.visibility(), profile.links(), profile.completenessScore(),
                profile.skills() == null ? List.of() : profile.skills());
    }

    private static ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("Candidate not found.");
    }
}
