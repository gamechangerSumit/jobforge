package com.jobforge.backend.profile.api;

import com.jobforge.backend.profile.app.ProfileService;
import com.jobforge.backend.profile.app.SeekerProfileUpdate;
import com.jobforge.backend.profile.domain.ExpectedSalary;
import com.jobforge.backend.profile.domain.Links;
import com.jobforge.backend.profile.domain.Location;
import com.jobforge.backend.profile.domain.ProfileVisibility;
import com.jobforge.backend.shared.security.AuthenticatedUser;
import com.jobforge.backend.shared.web.IfMatch;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API_CONTRACT §12.2 — {@code /seekers/me/profile} (JOB_SEEKER only). */
@RestController
@RequestMapping("/seekers/me/profile")
@PreAuthorize("hasRole('JOB_SEEKER')")
public class SeekerProfileController {

    private final ProfileService profiles;

    public SeekerProfileController(ProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping
    public ResponseEntity<SeekerProfileResponse> get(@AuthenticationPrincipal AuthenticatedUser caller) {
        return respond(SeekerProfileResponse.from(profiles.getSeekerProfile(caller.id())));
    }

    @PutMapping
    public ResponseEntity<SeekerProfileResponse> put(
            @AuthenticationPrincipal AuthenticatedUser caller,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @Valid @RequestBody SeekerProfileRequest request) {
        SeekerProfileUpdate update = new SeekerProfileUpdate(
                request.headline(), request.summary(), request.phone(),
                request.location() == null ? null
                        : new Location(request.location().city(), request.location().state(), request.location().country()),
                request.currentTitle(), request.yearsExperience(),
                request.expectedSalary() == null ? null
                        : new ExpectedSalary(request.expectedSalary().min(), request.expectedSalary().max(),
                                request.expectedSalary().currency(), request.expectedSalary().period()),
                request.noticePeriodDays(),
                request.openToWork() == null || request.openToWork(),
                request.visibility() == null ? ProfileVisibility.RECRUITERS_ONLY : request.visibility(),
                request.links() == null ? null
                        : new Links(request.links().linkedin(), request.links().github(), request.links().portfolio()));
        return respond(SeekerProfileResponse.from(
                profiles.replaceSeekerProfile(caller.id(), update, IfMatch.parse(ifMatch))));
    }

    private static ResponseEntity<SeekerProfileResponse> respond(SeekerProfileResponse body) {
        return ResponseEntity.ok().eTag("\"" + body.version() + "\"").body(body);
    }
}
