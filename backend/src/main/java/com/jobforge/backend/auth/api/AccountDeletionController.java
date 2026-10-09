package com.jobforge.backend.auth.api;

import com.jobforge.backend.auth.app.AuthService;
import com.jobforge.backend.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API_CONTRACT 12.2 - DELETE /users/me {password}: anonymizes the account (any authenticated role except ADMIN). */
@RestController
@RequestMapping("/users/me")
public class AccountDeletionController {

    public record DeleteAccountRequest(@NotBlank @Size(max = 64) String password) {
        @Override
        public String toString() {
            return "DeleteAccountRequest[]";
        }
    }

    private final AuthService auth;
    private final RefreshCookieFactory cookies;

    public AccountDeletionController(AuthService auth, RefreshCookieFactory cookies) {
        this.auth = auth;
        this.cookies = cookies;
    }

    @DeleteMapping
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser caller,
            @Valid @RequestBody DeleteAccountRequest request) {
        auth.deleteAccount(caller.id(), request.password());
        return ResponseEntity.noContent().header("Set-Cookie", cookies.clear().toString()).build();
    }
}
