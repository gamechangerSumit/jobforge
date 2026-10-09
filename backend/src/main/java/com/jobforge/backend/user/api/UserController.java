package com.jobforge.backend.user.api;

import com.jobforge.backend.shared.security.AuthenticatedUser;
import com.jobforge.backend.user.app.UserAccountService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.jobforge.backend.shared.error.ApiException;
import com.jobforge.backend.shared.error.ErrorCode;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API_CONTRACT §12.2 — {@code /users/me} (any authenticated role). */
@RestController
@RequestMapping("/users")
public class UserController {

    private final UserAccountService users;

    public UserController(UserAccountService users) {
        this.users = users;
    }

    @GetMapping("/me")
    public UserAccountResponse me(@AuthenticationPrincipal AuthenticatedUser caller) {
        return UserAccountResponse.from(users.getMe(caller.id()), users.avatarUrl(caller.id()));
    }

    @PatchMapping("/me")
    public UserAccountResponse update(
            @AuthenticationPrincipal AuthenticatedUser caller, @Valid @RequestBody UpdateUserRequest request) {
        return UserAccountResponse.from(users.updateMe(caller.id(), request.firstName(), request.lastName(), request.handle()),
                users.avatarUrl(caller.id()));
    }

    /** API_CONTRACT 12.2 - multipart image (PNG/JPEG/WEBP, max 2 MB, magic bytes verified). */
    @PutMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UserAccountResponse uploadAvatar(
            @AuthenticationPrincipal AuthenticatedUser caller, @RequestParam("file") MultipartFile file) {
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ApiException(ErrorCode.MALFORMED_REQUEST, "The file could not be read.");
        }
        users.uploadAvatar(caller.id(), bytes);
        return UserAccountResponse.from(users.getMe(caller.id()), users.avatarUrl(caller.id()));
    }

    /** Public (see UserSecurityRulesContributor): browsers cannot send the in-memory bearer token from an img tag. */
    @GetMapping("/{id}/avatar")
    public ResponseEntity<byte[]> avatar(@PathVariable UUID id) {
        UserAccountService.AvatarContent avatar = users.loadAvatar(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(avatar.contentType()))
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
                .body(avatar.bytes());
    }
}
