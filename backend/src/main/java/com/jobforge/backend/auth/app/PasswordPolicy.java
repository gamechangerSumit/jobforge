package com.jobforge.backend.auth.app;

import com.jobforge.backend.shared.error.ValidationFailedException;
import java.nio.charset.StandardCharsets;

/** API_CONTRACT §10: 10–64 chars, ≥1 letter and ≥1 digit, not equal to the email. */
public final class PasswordPolicy {

    public static final String LETTER_AND_DIGIT = "^(?=.*[A-Za-z])(?=.*\\d).*$";
    private static final int BCRYPT_MAX_BYTES = 72;

    private PasswordPolicy() {}

    /** Rules that Bean Validation cannot express. */
    public static void check(String field, String password, String email) {
        if (password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            throw ValidationFailedException.of(field, "SIZE", "is too long");
        }
        if (email != null && password.equalsIgnoreCase(email)) {
            throw ValidationFailedException.of(field, "PATTERN", "must not equal the email address");
        }
    }
}
