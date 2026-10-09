package com.jobforge.backend.application.api;

import com.jobforge.backend.application.app.ApplicationViews.CandidateView;
import com.jobforge.backend.application.app.CandidateService;
import com.jobforge.backend.shared.security.AuthenticatedUser;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /seekers/{userId}} (API_CONTRACT §12.2, RC-3). Lives in the application module because visibility depends
 * on applications; this avoids a profile↔application module cycle.
 */
@RestController
@RequestMapping("/seekers/{userId}")
@PreAuthorize("hasAnyRole('RECRUITER','ADMIN')")
public class CandidateController {

    private final CandidateService candidates;

    public CandidateController(CandidateService candidates) {
        this.candidates = candidates;
    }

    @GetMapping
    public CandidateView get(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID userId) {
        return candidates.view(caller, userId);
    }
}
