package com.jobforge.backend.profile.app;

import com.jobforge.backend.profile.domain.ApprovalStatus;
import com.jobforge.backend.profile.domain.ExpectedSalary;
import com.jobforge.backend.profile.domain.RecruiterProfile;
import com.jobforge.backend.profile.domain.SeekerProfile;
import com.jobforge.backend.profile.facade.ProfileFacade;
import com.jobforge.backend.shared.domain.UserRole;
import com.jobforge.backend.shared.error.ConflictException;
import com.jobforge.backend.shared.error.ErrorCode;
import com.jobforge.backend.shared.error.ResourceNotFoundException;
import com.jobforge.backend.shared.error.ValidationFailedException;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Seeker and recruiter profile use cases. Callers are authorized by role at the API edge; ownership = own userId. */
@Service
public class ProfileService implements ProfileFacade {

    private final SeekerProfileRepository seekers;
    private final RecruiterProfileRepository recruiters;
    private final Clock clock;

    public ProfileService(SeekerProfileRepository seekers, RecruiterProfileRepository recruiters, Clock clock) {
        this.seekers = seekers;
        this.recruiters = recruiters;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void createInitialProfile(UUID userId, UserRole role) {
        switch (role) {
            case JOB_SEEKER -> seekers.insertEmpty(UUID.randomUUID(), userId, clock.instant());
            case RECRUITER -> recruiters.insertEmpty(UUID.randomUUID(), userId, clock.instant());
            case ADMIN -> { /* admins have no role profile */ }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isRecruiterApproved(UUID userId) {
        return recruiters.findByUserId(userId)
                .map(p -> p.approvalStatus() == ApprovalStatus.APPROVED)
                .orElse(false);
    }

    @Override
    @Transactional
    public void scrubOnAccountDeletion(UUID userId) {
        seekers.scrub(userId, clock.instant());
    }

    // ---- seeker ----

    @Transactional(readOnly = true)
    public SeekerProfile getSeekerProfile(UUID userId) {
        return seekers.findByUserId(userId).orElseThrow(() -> new ResourceNotFoundException("Profile not found."));
    }

    @Transactional
    public SeekerProfile replaceSeekerProfile(UUID userId, SeekerProfileUpdate update, Long ifMatchVersion) {
        SeekerProfile current = getSeekerProfile(userId);
        if (ifMatchVersion != null && ifMatchVersion != current.version()) {
            throw staleVersion();
        }
        validateSalary(update.expectedSalary());
        if (!seekers.replace(current.id(), update, current.version(), clock.instant())) {
            throw staleVersion();
        }
        seekers.recalculateCompleteness(current.id());
        return getSeekerProfile(userId);
    }

    private static void validateSalary(ExpectedSalary salary) {
        if (salary != null && salary.min() != null && salary.max() != null && salary.max() < salary.min()) {
            throw ValidationFailedException.of("expectedSalary.max", "MIN", "must be greater than or equal to min");
        }
    }

    private static ConflictException staleVersion() {
        return new ConflictException(ErrorCode.STALE_VERSION, "The profile was modified by someone else. Reload and retry.");
    }

    // ---- recruiter ----

    @Transactional(readOnly = true)
    public RecruiterProfile getRecruiterProfile(UUID userId) {
        return recruiters.findByUserId(userId).orElseThrow(() -> new ResourceNotFoundException("Profile not found."));
    }

    @Transactional
    public RecruiterProfile replaceRecruiterProfile(UUID userId, String jobTitle, String phone) {
        getRecruiterProfile(userId);
        recruiters.update(userId, jobTitle, phone, clock.instant());
        return getRecruiterProfile(userId);
    }
}
