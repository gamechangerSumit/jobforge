package com.jobforge.backend.interview.api;

import com.jobforge.backend.interview.app.InterviewCommands;
import com.jobforge.backend.interview.app.InterviewService;
import com.jobforge.backend.interview.app.InterviewViews.InterviewView;
import com.jobforge.backend.interview.domain.InterviewStatus;
import com.jobforge.backend.shared.api.PagedResponse;
import com.jobforge.backend.shared.config.ApiPaths;
import com.jobforge.backend.shared.error.ValidationFailedException;
import com.jobforge.backend.shared.security.AuthenticatedUser;
import com.jobforge.backend.shared.web.QueryParams;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * API_CONTRACT §12.7 interviews. Roles: recruiters (own company) and seekers (own interviews) only; ADMIN is not
 * listed in the contract and therefore receives 403. Ownership is enforced in {@link InterviewService}.
 */
@RestController
public class InterviewController {

    private final InterviewService interviews;

    public InterviewController(InterviewService interviews) {
        this.interviews = interviews;
    }

    @PostMapping("/applications/{id}/interviews")
    @PreAuthorize("hasRole('RECRUITER')")
    public ResponseEntity<InterviewView> schedule(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id,
            @Valid @RequestBody InterviewRequests.Schedule request) {
        InterviewView view = interviews.schedule(caller, id, new InterviewCommands.Schedule(request.type(), request.scheduledAt(),
                request.durationMinutes(), request.timezone(), request.locationOrLink(), request.notes()));
        return ResponseEntity.created(URI.create(ApiPaths.BASE + "/interviews/" + view.id())).body(view);
    }

    @GetMapping("/interviews")
    @PreAuthorize("hasAnyRole('JOB_SEEKER','RECRUITER')")
    public PagedResponse<InterviewView> list(@AuthenticationPrincipal AuthenticatedUser caller, HttpServletRequest http) {
        QueryParams q = new QueryParams(http, "applicationId", "from", "to", "status", "page", "size");
        int page = q.page();
        int size = q.size();
        InterviewCommands.Filter filter = new InterviewCommands.Filter(q.uuid("applicationId"), q.enumValue("status", InterviewStatus.class),
                bound(q.string("from"), "from", false), bound(q.string("to"), "to", true));
        return interviews.list(caller, filter, page, size).toResponse(Function.identity(), page, size);
    }

    @GetMapping("/interviews/{id}")
    @PreAuthorize("hasAnyRole('JOB_SEEKER','RECRUITER')")
    public InterviewView get(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id) {
        return interviews.get(caller, id);
    }

    @PatchMapping("/interviews/{id}")
    @PreAuthorize("hasRole('RECRUITER')")
    public InterviewView update(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id,
            @Valid @RequestBody InterviewRequests.Update request) {
        return interviews.update(caller, id, new InterviewCommands.Update(request.type(), request.scheduledAt(),
                request.durationMinutes(), request.timezone(), request.locationOrLink(), request.notes()));
    }

    @PostMapping("/interviews/{id}/cancel")
    @PreAuthorize("hasRole('RECRUITER')")
    public InterviewView cancel(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id,
            @Valid @RequestBody InterviewRequests.Cancel request) {
        return interviews.cancel(caller, id, new InterviewCommands.Cancel(request.reason()));
    }

    @PostMapping("/interviews/{id}/respond")
    @PreAuthorize("hasRole('JOB_SEEKER')")
    public InterviewView respond(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id,
            @Valid @RequestBody InterviewRequests.Respond request) {
        return interviews.respond(caller, id, new InterviewCommands.Respond(request.response(), request.note()));
    }

    @PostMapping("/interviews/{id}/complete")
    @PreAuthorize("hasRole('RECRUITER')")
    public InterviewView complete(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id,
            @Valid @RequestBody InterviewRequests.Complete request) {
        return interviews.complete(caller, id, new InterviewCommands.Complete(request.outcome()));
    }

    /** ISO-8601 instant, or a plain date (start of that day for {@code from}, end of that day for {@code to}), UTC. */
    private static Instant bound(String value, String name, boolean endOfDay) {
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            // fall through to the date form
        }
        try {
            LocalDate date = LocalDate.parse(value);
            Instant start = date.atStartOfDay().toInstant(ZoneOffset.UTC);
            return endOfDay ? start.plusSeconds(86_400 - 1) : start;
        } catch (DateTimeParseException e) {
            throw ValidationFailedException.of(name, "PATTERN", "must be an ISO-8601 instant (…Z) or a date (YYYY-MM-DD)");
        }
    }
}
