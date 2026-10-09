package com.jobforge.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobforge.backend.auth.app.PasswordPolicy;
import com.jobforge.backend.auth.app.TokenCodec;
import com.jobforge.backend.shared.error.ValidationFailedException;
import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

    @Test
    void rejectsPasswordEqualToEmail() {
        assertThatThrownBy(() -> PasswordPolicy.check("password", "A1@Example.test", "a1@example.test"))
                .isInstanceOf(ValidationFailedException.class);
    }

    @Test
    void rejectsPasswordsLongerThanBcryptLimitInBytes() {
        String multibyte = "é".repeat(40) + "a1"; // 40 chars but 80+ bytes
        assertThatThrownBy(() -> PasswordPolicy.check("password", multibyte, "x@example.test"))
                .isInstanceOf(ValidationFailedException.class);
    }

    @Test
    void acceptsNormalPassword() {
        assertThatCode(() -> PasswordPolicy.check("password", "Correct1Horse", "x@example.test")).doesNotThrowAnyException();
    }

    @Test
    void letterAndDigitRegex() {
        assertThat("abcdefghij".matches(PasswordPolicy.LETTER_AND_DIGIT)).isFalse();
        assertThat("1234567890".matches(PasswordPolicy.LETTER_AND_DIGIT)).isFalse();
        assertThat("abcdefghi1".matches(PasswordPolicy.LETTER_AND_DIGIT)).isTrue();
    }

    @Test
    void tokensAreUniqueUrlSafeAndHashedDeterministically() {
        String a = TokenCodec.newToken();
        assertThat(a).isNotEqualTo(TokenCodec.newToken()).matches("^[A-Za-z0-9_-]{43}$");
        assertThat(TokenCodec.sha256Hex(a)).hasSize(64).isEqualTo(TokenCodec.sha256Hex(a)).doesNotContain(a);
    }
}
