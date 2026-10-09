package com.jobforge.backend.auth.api;

import com.jobforge.backend.auth.app.AuthService;
import com.jobforge.backend.shared.domain.UserRole;
import com.jobforge.backend.shared.security.AuthenticatedUser;
import com.jobforge.backend.user.facade.UserAccountView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** API_CONTRACT 12.14 - POST /admin/users: create an ADMIN and mail a set-password link. */
@RestController
@RequestMapping("/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAccountController {

    public record CreateAdminRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 1, max = 60) String firstName,
            @NotBlank @Size(min = 1, max = 60) String lastName) {}

    public record CreatedAdminResponse(UUID id, String email, String firstName, String lastName, String handle,
            UserRole role, String status, Instant createdAt) {

        static CreatedAdminResponse from(UserAccountView u) {
            return new CreatedAdminResponse(u.id(), u.email(), u.firstName(), u.lastName(), u.handle(), u.role(),
                    u.status().name(), u.createdAt());
        }
    }

    private final AuthService auth;

    public AdminAccountController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreatedAdminResponse create(@AuthenticationPrincipal AuthenticatedUser admin,
            @Valid @RequestBody CreateAdminRequest request) {
        return CreatedAdminResponse.from(auth.createAdmin(admin.id(), request.email(), request.firstName(), request.lastName()));
    }
}
