package com.jobforge.backend.auth.api;

import com.jobforge.backend.auth.api.AuthRequests.ChangePassword;
import com.jobforge.backend.auth.api.AuthRequests.EmailOnly;
import com.jobforge.backend.auth.api.AuthRequests.Login;
import com.jobforge.backend.auth.api.AuthRequests.Register;
import com.jobforge.backend.auth.api.AuthRequests.ResetPassword;
import com.jobforge.backend.auth.api.AuthRequests.Token;
import com.jobforge.backend.auth.app.AuthResults.Session;
import com.jobforge.backend.auth.app.AuthService;
import com.jobforge.backend.auth.app.AuthService.RegisterCommand;
import com.jobforge.backend.shared.domain.UserRole;
import com.jobforge.backend.shared.error.ApiException;
import com.jobforge.backend.shared.error.ErrorCode;
import com.jobforge.backend.shared.security.AuthenticatedUser;
import com.jobforge.backend.shared.web.ClientInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API_CONTRACT §3 and §12.1. Thin: delegates to {@link AuthService}. */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final String CSRF_HEADER = "X-Requested-With";
    private static final String CSRF_VALUE = "JobForge";

    private final AuthService auth;
    private final RefreshCookieFactory cookies;

    public AuthController(AuthService auth, RefreshCookieFactory cookies) {
        this.auth = auth;
        this.cookies = cookies;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponses.Registered> register(@Valid @RequestBody Register request) {
        UserRole role = UserRole.valueOf(request.role().name());
        var user = auth.register(new RegisterCommand(request.email(), request.password(), request.firstName(),
                request.lastName(), role, request.handle()));
        return ResponseEntity.status(HttpStatus.CREATED).body(AuthResponses.Registered.from(user));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponses.Login> login(@Valid @RequestBody Login request, HttpServletRequest http) {
        Session session = auth.login(request.email(), request.password(), ClientInfo.from(http));
        return withCookie(session);
    }

    /** Cookie + {@code X-Requested-With: JobForge}. Response is the login shape (superset of "new access token"). */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponses.Login> refresh(
            @CookieValue(name = RefreshCookieFactory.NAME, required = false) String refreshToken,
            @RequestHeader(name = CSRF_HEADER, required = false) String requestedWith,
            HttpServletRequest http) {
        if (!CSRF_VALUE.equals(requestedWith)) {
            throw new ApiException(ErrorCode.AUTH_REFRESH_INVALID, "Refresh token is missing or invalid.");
        }
        return withCookie(auth.refresh(refreshToken, ClientInfo.from(http)));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @AuthenticationPrincipal AuthenticatedUser caller,
            @CookieValue(name = RefreshCookieFactory.NAME, required = false) String refreshToken) {
        auth.logout(caller.id(), caller.role(), refreshToken);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookies.clear()).build();
    }

    @PostMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(@Valid @RequestBody Token request) {
        auth.verifyEmail(request.token());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<Void> resendVerification(@Valid @RequestBody EmailOnly request) {
        auth.resendVerification(request.email());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody EmailOnly request) {
        auth.forgotPassword(request.email());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPassword request) {
        auth.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal AuthenticatedUser caller,
            @CookieValue(name = RefreshCookieFactory.NAME, required = false) String refreshToken,
            @Valid @RequestBody ChangePassword request) {
        auth.changePassword(caller.id(), request.currentPassword(), request.newPassword(), refreshToken);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public AuthResponses.Me me(@AuthenticationPrincipal AuthenticatedUser caller) {
        return AuthResponses.Me.from(auth.me(caller.id()));
    }

    private ResponseEntity<AuthResponses.Login> withCookie(Session session) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.issue(session.refreshToken(), session.refreshMaxAgeSeconds()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(AuthResponses.Login.from(session));
    }
}
