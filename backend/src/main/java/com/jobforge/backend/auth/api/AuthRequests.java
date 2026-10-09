package com.jobforge.backend.auth.api;

import com.jobforge.backend.auth.app.PasswordPolicy;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Request bodies (API_CONTRACT §12.1, limits §10). Records holding passwords never print them. */
public final class AuthRequests {

    private AuthRequests() {}

    /** {@code role ∈ JOB_SEEKER|RECRUITER}: ADMIN is rejected as INVALID_ENUM. */
    public enum RegisterRole { JOB_SEEKER, RECRUITER }

    public record Register(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 10, max = 64) @Pattern(regexp = PasswordPolicy.LETTER_AND_DIGIT) String password,
            @NotBlank @Size(min = 1, max = 60) String firstName,
            @NotBlank @Size(min = 1, max = 60) String lastName,
            @NotNull RegisterRole role,
            @Size(min = 3, max = 30) @Pattern(regexp = "^[a-z0-9_]+$") String handle) {
        @Override
        public String toString() {
            return "Register[role=" + role + "]";
        }
    }

    public record Login(@NotBlank @Size(max = 254) String email, @NotBlank @Size(max = 64) String password) {
        @Override
        public String toString() {
            return "Login[]";
        }
    }

    public record Token(@NotBlank @Size(max = 200) String token) {
        @Override
        public String toString() {
            return "Token[]";
        }
    }

    public record EmailOnly(@NotBlank @Email @Size(max = 254) String email) {}

    public record ResetPassword(
            @NotBlank @Size(max = 200) String token,
            @NotBlank @Size(min = 10, max = 64) @Pattern(regexp = PasswordPolicy.LETTER_AND_DIGIT) String newPassword) {
        @Override
        public String toString() {
            return "ResetPassword[]";
        }
    }

    public record ChangePassword(
            @NotBlank @Size(max = 64) String currentPassword,
            @NotBlank @Size(min = 10, max = 64) @Pattern(regexp = PasswordPolicy.LETTER_AND_DIGIT) String newPassword) {
        @Override
        public String toString() {
            return "ChangePassword[]";
        }
    }
}
