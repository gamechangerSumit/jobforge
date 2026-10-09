package com.jobforge.backend.profile.api;

import com.jobforge.backend.profile.app.ProfileService;
import com.jobforge.backend.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API_CONTRACT §12.3 — {@code /recruiters/me} (RECRUITER only; the endpoint is own-record by construction). */
@RestController
@RequestMapping("/recruiters/me")
@PreAuthorize("hasRole('RECRUITER')")
public class RecruiterProfileController {

    private final ProfileService profiles;

    public RecruiterProfileController(ProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping
    public RecruiterProfileResponse get(@AuthenticationPrincipal AuthenticatedUser caller) {
        return RecruiterProfileResponse.from(profiles.getRecruiterProfile(caller.id()));
    }

    @PutMapping
    public RecruiterProfileResponse put(
            @AuthenticationPrincipal AuthenticatedUser caller, @Valid @RequestBody RecruiterProfileRequest request) {
        return RecruiterProfileResponse.from(
                profiles.replaceRecruiterProfile(caller.id(), request.jobTitle(), request.phone()));
    }
}
