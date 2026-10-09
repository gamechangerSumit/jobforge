package com.jobforge.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobforge.backend.support.ApiIntegrationTestSupport;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** API_CONTRACT §3 / §12.1 flows. Requires Docker. */
class AuthFlowIT extends ApiIntegrationTestSupport {

    @Autowired JwtEncoder jwtEncoder;

    private MockHttpServletRequestBuilder refresh(String token) {
        MockHttpServletRequestBuilder b = post(API + "/auth/refresh").header("X-Requested-With", "JobForge");
        return token == null ? b : b.cookie(new Cookie("jf_refresh", token));
    }

    // ---------------------------------------------------------------- register

    @Test
    void registerCreatesPendingUserWithProfileAndAudit() throws Exception {
        String id = unique();
        int before = auditCount("USER_REGISTERED");
        mvc.perform(postJson("/auth/register", Map.of("email", "R" + id + "@Example.test", "password", "Aa1" + id,
                        "firstName", "Ada", "lastName", "Lovelace", "role", "JOB_SEEKER")))
                .andExpect(status().isCreated())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.data.user.email").value("r" + id + "@example.test"))
                .andExpect(jsonPath("$.data.user.role").value("JOB_SEEKER"))
                .andExpect(jsonPath("$.data.user.emailVerified").value(false))
                .andExpect(jsonPath("$.data.user.handle").value(org.hamcrest.Matchers.matchesPattern("^[a-z0-9_]{3,30}$")))
                .andExpect(content().string(not(containsString("passwordHash"))))
                .andExpect(content().string(not(containsString("$2a$"))));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM core.seeker_profiles sp JOIN core.users u ON u.id = sp.user_id WHERE u.email = ?",
                Integer.class, "r" + id + "@example.test")).isEqualTo(1);
        assertThat(auditCount("USER_REGISTERED")).isEqualTo(before + 1);
        assertThat(jdbc.queryForObject("SELECT password_hash FROM core.users WHERE email = ?", String.class, "r" + id + "@example.test"))
                .startsWith("$2");
    }

    @Test
    void registerRecruiterCreatesPendingRecruiterProfile() throws Exception {
        Account a = register("RECRUITER");
        assertThat(jdbc.queryForObject("SELECT rp.approval_status FROM core.recruiter_profiles rp JOIN core.users u ON u.id = rp.user_id WHERE u.email = ?",
                String.class, a.email())).isEqualTo("PENDING");
    }

    @Test
    void duplicateEmailAndHandleAreConflicts() throws Exception {
        Account a = register("JOB_SEEKER");
        mvc.perform(postJson("/auth/register", Map.of("email", a.email().toUpperCase(), "password", a.password(),
                        "firstName", "A", "lastName", "B", "role", "JOB_SEEKER")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("EMAIL_ALREADY_REGISTERED"));
        mvc.perform(postJson("/auth/register", Map.of("email", "o" + unique() + "@example.test", "password", a.password(),
                        "firstName", "A", "lastName", "B", "role", "JOB_SEEKER", "handle", a.handle())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("HANDLE_TAKEN"));
    }

    @Test
    void registerValidationFailuresUseContractDetails() throws Exception {
        mvc.perform(postJson("/auth/register", Map.of("email", "not-an-email", "password", "short1", "firstName", "",
                        "lastName", "L", "role", "JOB_SEEKER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.details[?(@.field=='email')].code", hasItem("EMAIL")))
                .andExpect(jsonPath("$.error.details[?(@.field=='password')].code", hasItem("SIZE")))
                .andExpect(jsonPath("$.error.details[?(@.field=='firstName')].code", hasItem("NOT_BLANK")));
        mvc.perform(postJson("/auth/register", Map.of("email", "a" + unique() + "@example.test", "password", "onlyletterspw",
                        "firstName", "A", "lastName", "L", "role", "JOB_SEEKER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[?(@.field=='password')].code", hasItem("PATTERN")));
    }

    @Test
    void adminCannotSelfRegisterAndUnknownFieldsAreRejected() throws Exception {
        mvc.perform(postJson("/auth/register", Map.of("email", "a" + unique() + "@example.test", "password", "Aa1" + unique(),
                        "firstName", "A", "lastName", "L", "role", "ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].code").value("INVALID_ENUM"));
        mvc.perform(postJson("/auth/register", Map.of("email", "a" + unique() + "@example.test", "password", "Aa1" + unique(),
                        "firstName", "A", "lastName", "L", "role", "JOB_SEEKER", "status", "ACTIVE")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].code").value("UNKNOWN_FIELD"));
    }

    // ---------------------------------------------------------------- verify / resend

    @Test
    void verifyEmailActivatesAccountAndTokenIsSingleUse() throws Exception {
        Account a = register("JOB_SEEKER");
        String token = mailer.verificationToken(a.email());
        assertThat(token).isNotBlank();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM core.verification_tokens WHERE token_hash = ?", Integer.class, token)).isZero(); // only the hash is stored
        mvc.perform(postJson("/auth/verify-email", Map.of("token", token))).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT status FROM core.users WHERE email = ?", String.class, a.email())).isEqualTo("ACTIVE");
        mvc.perform(postJson("/auth/verify-email", Map.of("token", token)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("BUSINESS_RULE_VIOLATED"));
        assertThat(auditCount("EMAIL_VERIFIED")).isPositive();
    }

    @Test
    void resendAndForgotNeverRevealWhetherTheEmailExists() throws Exception {
        mvc.perform(postJson("/auth/resend-verification", Map.of("email", "nobody" + unique() + "@example.test"))).andExpect(status().isNoContent());
        mvc.perform(postJson("/auth/forgot-password", Map.of("email", "nobody" + unique() + "@example.test"))).andExpect(status().isNoContent());
    }

    // ---------------------------------------------------------------- login

    @Test
    void loginIssuesAccessTokenAndHardenedRefreshCookie() throws Exception {
        Account a = register("JOB_SEEKER");
        MvcResult r = loginResult(a);
        assertThat(r.getResponse().getStatus()).isEqualTo(200); // unverified users may sign in (emailVerified=false)
        String cookie = r.getResponse().getHeader("Set-Cookie");
        assertThat(cookie).startsWith("jf_refresh=").contains("HttpOnly").contains("SameSite=Strict")
                .contains("Path=/api/v1/auth").contains("Max-Age=1209600");
        mvc.perform(postJson("/auth/login", Map.of("email", a.email(), "password", a.password())))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").value(900))
                .andExpect(jsonPath("$.data.user.emailVerified").value(false))
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM core.refresh_tokens rt JOIN core.users u ON u.id = rt.user_id WHERE u.email = ? AND rt.token_hash = ?",
                Integer.class, a.email(), toSession(r).refreshToken())).isZero(); // raw token never stored
        assertThat(auditCount("USER_LOGIN_SUCCESS")).isPositive();
    }

    @Test
    void wrongPasswordAndUnknownEmailGiveTheSameErrorAndAuditAFailure() throws Exception {
        Account a = register("JOB_SEEKER");
        int before = auditCount("USER_LOGIN_FAILED");
        mvc.perform(postJson("/auth/login", Map.of("email", a.email(), "password", "Wrong" + unique())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_INVALID_CREDENTIALS"));
        mvc.perform(postJson("/auth/login", Map.of("email", "x" + unique() + "@example.test", "password", "Wrong1" + unique())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_INVALID_CREDENTIALS"));
        assertThat(auditCount("USER_LOGIN_FAILED")).isEqualTo(before + 2); // persisted despite the error response
    }

    @Test
    void suspendedAccountsCannotLoginOrUseExistingTokens() throws Exception {
        Account a = register("JOB_SEEKER");
        Session s = ready(a);
        jdbc.update("UPDATE core.users SET status = 'SUSPENDED' WHERE email = ?", a.email());
        mvc.perform(postJson("/auth/login", Map.of("email", a.email(), "password", a.password())))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("ACCOUNT_SUSPENDED"));
        mvc.perform(authedGet("/auth/me", s))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("ACCOUNT_SUSPENDED"));
    }

    // ---------------------------------------------------------------- access token

    @Test
    void meReturnsFlagsAndRejectsMissingOrBadTokens() throws Exception {
        Account a = register("RECRUITER");
        Session s = ready(a);
        mvc.perform(authedGet("/auth/me", s))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("RECRUITER"))
                .andExpect(jsonPath("$.data.emailVerified").value(true))
                .andExpect(jsonPath("$.data.recruiterApproved").value(false))
                .andExpect(jsonPath("$.data.companyVerified").value(false));
        mvc.perform(get(API + "/auth/me")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_UNAUTHENTICATED"));
        mvc.perform(get(API + "/auth/me").header("Authorization", "Bearer not.a.jwt")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_UNAUTHENTICATED"));
    }

    @Test
    void expiredAccessTokenIsReportedAsExpired() throws Exception {
        Account a = register("JOB_SEEKER");
        ready(a);
        UUID id = jdbc.queryForObject("SELECT id FROM core.users WHERE email = ?", UUID.class, a.email());
        Instant past = Instant.now().minusSeconds(3600);
        String token = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder().subject(id.toString()).issuedAt(past.minusSeconds(900)).expiresAt(past)
                        .claim("role", "JOB_SEEKER").claim("ver", 0).build())).getTokenValue();
        mvc.perform(get(API + "/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_TOKEN_EXPIRED"));
    }

    // ---------------------------------------------------------------- refresh rotation

    @Test
    void refreshRotatesAndReuseRevokesTheWholeFamily() throws Exception {
        Session first = ready(register("JOB_SEEKER"));
        MvcResult second = mvc.perform(refresh(first.refreshToken())).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty()).andReturn();
        Session rotated = toSession(second);
        assertThat(rotated.refreshToken()).isNotEqualTo(first.refreshToken());
        mvc.perform(authedGet("/auth/me", rotated)).andExpect(status().isOk());

        int reuseBefore = auditCount("TOKEN_REUSE_DETECTED");
        mvc.perform(refresh(first.refreshToken())).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_REFRESH_REUSED"));
        assertThat(auditCount("TOKEN_REUSE_DETECTED")).isEqualTo(reuseBefore + 1);
        mvc.perform(refresh(rotated.refreshToken())).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_REFRESH_INVALID")); // family is dead
    }

    @Test
    void refreshRequiresCookieAndCsrfHeader() throws Exception {
        Session s = ready(register("JOB_SEEKER"));
        mvc.perform(post(API + "/auth/refresh").cookie(new Cookie("jf_refresh", s.refreshToken())))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("AUTH_REFRESH_INVALID"));
        mvc.perform(refresh(null)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_REFRESH_INVALID"));
        mvc.perform(refresh("garbage")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_REFRESH_INVALID"));
    }

    @Test
    void logoutRevokesTheSessionAndClearsTheCookie() throws Exception {
        Session s = ready(register("JOB_SEEKER"));
        mvc.perform(authed(post(API + "/auth/logout"), s).cookie(new Cookie("jf_refresh", s.refreshToken())))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));
        mvc.perform(refresh(s.refreshToken())).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_REFRESH_INVALID"));
        assertThat(auditCount("USER_LOGOUT")).isPositive();
        mvc.perform(post(API + "/auth/logout")).andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- password reset / change

    @Test
    void passwordResetKillsAllSessionsAndTokenIsSingleUse() throws Exception {
        Account a = register("JOB_SEEKER");
        Session old = ready(a);
        mvc.perform(postJson("/auth/forgot-password", Map.of("email", a.email()))).andExpect(status().isNoContent());
        String token = mailer.resetToken(a.email());

        mvc.perform(postJson("/auth/reset-password", Map.of("token", token, "newPassword", "tooshort")))
                .andExpect(status().isBadRequest()); // a weak password must not burn the token
        String newPassword = "Zz9" + unique();
        mvc.perform(postJson("/auth/reset-password", Map.of("token", token, "newPassword", newPassword)))
                .andExpect(status().isNoContent());
        mvc.perform(postJson("/auth/reset-password", Map.of("token", token, "newPassword", "Yy8" + unique())))
                .andExpect(status().isUnprocessableEntity());

        mvc.perform(authedGet("/auth/me", old)).andExpect(status().isUnauthorized()); // token_version bumped
        mvc.perform(refresh(old.refreshToken())).andExpect(status().isUnauthorized());
        mvc.perform(postJson("/auth/login", Map.of("email", a.email(), "password", a.password()))).andExpect(status().isUnauthorized());
        mvc.perform(postJson("/auth/login", Map.of("email", a.email(), "password", newPassword))).andExpect(status().isOk());
        assertThat(auditCount("PASSWORD_RESET")).isPositive();
    }

    @Test
    void changePasswordKeepsCurrentSessionAndRevokesOthers() throws Exception {
        Account a = register("JOB_SEEKER");
        Session current = ready(a);
        Session other = login(a);
        String newPassword = "Nn5" + unique();

        mvc.perform(authed(postJson("/auth/change-password", Map.of("currentPassword", "Wrong1" + unique(), "newPassword", newPassword)), current))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("AUTH_INVALID_CREDENTIALS"));
        mvc.perform(authed(postJson("/auth/change-password", Map.of("currentPassword", a.password(), "newPassword", newPassword)), current)
                        .cookie(new Cookie("jf_refresh", current.refreshToken())))
                .andExpect(status().isNoContent());

        mvc.perform(refresh(current.refreshToken())).andExpect(status().isOk());
        mvc.perform(refresh(other.refreshToken())).andExpect(status().isUnauthorized());
        mvc.perform(postJson("/auth/login", Map.of("email", a.email(), "password", newPassword))).andExpect(status().isOk());
        assertThat(auditCount("PASSWORD_CHANGED")).isPositive();
    }
}
