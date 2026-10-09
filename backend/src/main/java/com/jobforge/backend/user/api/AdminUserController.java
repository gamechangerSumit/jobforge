package com.jobforge.backend.user.api;

import com.jobforge.backend.shared.api.PagedResponse;
import com.jobforge.backend.shared.error.ValidationFailedException;
import com.jobforge.backend.shared.security.AuthenticatedUser;
import com.jobforge.backend.shared.web.QueryParams;
import com.jobforge.backend.user.app.UserDirectory.AdminUserRow;
import com.jobforge.backend.user.app.UserDirectoryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API_CONTRACT 12.14 - admin user management (ADMIN only). */
@RestController
@RequestMapping("/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    public record StatusRequest(
            @NotBlank @Pattern(regexp = "ACTIVE|SUSPENDED") String status,
            @NotBlank @Size(max = 500) String reason) {}

    private static final List<String> ROLES = List.of("JOB_SEEKER", "RECRUITER", "ADMIN");
    private static final List<String> STATUSES = List.of("PENDING_VERIFICATION", "ACTIVE", "SUSPENDED");

    private final UserDirectoryService directory;

    public AdminUserController(UserDirectoryService directory) {
        this.directory = directory;
    }

    @GetMapping
    public PagedResponse<AdminUserRow> list(HttpServletRequest request) {
        QueryParams p = new QueryParams(request, "q", "role", "status", "page", "size");
        String role = p.string("role");
        String status = p.string("status");
        if (role != null && !ROLES.contains(role)) {
            throw ValidationFailedException.of("role", "INVALID_ENUM", "must be one of the allowed values");
        }
        if (status != null && !STATUSES.contains(status)) {
            throw ValidationFailedException.of("status", "INVALID_ENUM", "must be one of the allowed values");
        }
        int page = p.page();
        int size = p.size();
        return directory.adminSearch(p.string("q"), role, status, page, size).toResponse(Function.identity(), page, size);
    }

    @GetMapping("/{id}")
    public AdminUserRow detail(@PathVariable UUID id) {
        return directory.adminDetail(id);
    }

    @PatchMapping("/{id}/status")
    public AdminUserRow status(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable UUID id,
            @Valid @RequestBody StatusRequest request) {
        return directory.changeStatus(admin, id, request.status(), request.reason());
    }

    @PostMapping("/{id}/force-logout")
    public ResponseEntity<Void> forceLogout(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable UUID id) {
        directory.forceLogout(admin, id);
        return ResponseEntity.noContent().build();
    }
}
