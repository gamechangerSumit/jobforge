package com.jobforge.backend.profile.facade;

import com.jobforge.backend.shared.domain.UserRole;
import java.util.UUID;

/** Public API of the profile module (seeker + recruiter profiles). */
public interface ProfileFacade {

    /** Creates the empty role profile row at registration (ARCHITECTURE §8 step 1). No-op for ADMIN. */
    void createInitialProfile(UUID userId, UserRole role);

    /** {@code recruiter_profiles.approval_status = APPROVED} (D-16). */
    boolean isRecruiterApproved(UUID userId);

    /** Account deletion: hides and scrubs the seeker profile (no-op for other roles). */
    void scrubOnAccountDeletion(UUID userId);
}
