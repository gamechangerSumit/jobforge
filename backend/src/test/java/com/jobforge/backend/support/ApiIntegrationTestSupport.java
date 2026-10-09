package com.jobforge.backend.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobforge.backend.auth.app.AuthMailer;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;

/** Shared full-context setup: one PostgreSQL 16 container per JVM, MockMvc, captured emails. Requires Docker. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ApiIntegrationTestSupport.TestMailConfig.class)
public abstract class ApiIntegrationTestSupport {

    protected static final String API = "/api/v1";
    protected static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16");

    static {
        PG.start();
        TestDatabase.createSchemas(PG);
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PG::getJdbcUrl);
        registry.add("spring.datasource.username", PG::getUsername);
        registry.add("spring.datasource.password", PG::getPassword);
        registry.add("jobforge.migration.url", PG::getJdbcUrl);
        registry.add("jobforge.migration.user", PG::getUsername);
        registry.add("jobforge.migration.password", PG::getPassword);
        registry.add("jobforge.security.jwt.secret", () -> UUID.randomUUID() + "-" + UUID.randomUUID()); // generated per run
    }

    @Autowired protected MockMvc mvc;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected ObjectMapper json;
    @Autowired protected CapturingMailer mailer;

    public record Account(String email, String password, String handle, String role) {}

    public record Session(String accessToken, String refreshToken) {}

    /** Captures the last token mailed per address (never logged). */
    public static class CapturingMailer implements AuthMailer {
        private final Map<String, String> verification = new ConcurrentHashMap<>();
        private final Map<String, String> reset = new ConcurrentHashMap<>();

        @Override public void sendVerification(String to, String firstName, String token) { verification.put(to, token); }
        @Override public void sendPasswordReset(String to, String firstName, String token) { reset.put(to, token); }

        public String verificationToken(String email) { return verification.get(email); }
        public String resetToken(String email) { return reset.get(email); }
    }

    @TestConfiguration
    static class TestMailConfig {
        @Bean @Primary CapturingMailer capturingMailer() { return new CapturingMailer(); }
    }

    // ---------------------------------------------------------------- helpers

    protected static String unique() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    protected MockHttpServletRequestBuilder postJson(String path, Object body) throws Exception {
        return post(API + path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
    }

    protected Account register(String role) throws Exception {
        String id = unique();
        Account account = new Account("u" + id + "@example.test", "Aa1" + unique(), "h_" + id, role);
        mvc.perform(postJson("/auth/register", Map.of("email", account.email(), "password", account.password(),
                        "firstName", "Test", "lastName", "User", "role", role, "handle", account.handle())))
                .andExpect(status().isCreated());
        return account;
    }

    protected void verifyEmail(Account account) throws Exception {
        mvc.perform(postJson("/auth/verify-email", Map.of("token", mailer.verificationToken(account.email()))))
                .andExpect(status().isNoContent());
    }

    protected MvcResult loginResult(Account account) throws Exception {
        return mvc.perform(postJson("/auth/login", Map.of("email", account.email(), "password", account.password()))).andReturn();
    }

    protected Session login(Account account) throws Exception {
        MvcResult result = loginResult(account);
        if (result.getResponse().getStatus() != 200) {
            throw new AssertionError("login failed: " + result.getResponse().getStatus());
        }
        return toSession(result);
    }

    protected Session toSession(MvcResult result) throws Exception {
        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        Cookie cookie = result.getResponse().getCookie("jf_refresh");
        return new Session(body.at("/data/accessToken").asText(), cookie == null ? null : cookie.getValue());
    }

    /** Registered, e-mail verified and logged in. */
    protected Session ready(Account account) throws Exception {
        verifyEmail(account);
        return login(account);
    }

    protected MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder builder, Session session) {
        return builder.header("Authorization", "Bearer " + session.accessToken());
    }

    protected MockHttpServletRequestBuilder authedGet(String path, Session session) {
        return authed(get(API + path), session);
    }

    /** A verified, logged-in ADMIN (promoted directly in the database; the seeker profile is removed first). */
    protected Session adminLogin() throws Exception {
        Account admin = register("JOB_SEEKER");
        verifyEmail(admin);
        jdbc.update("DELETE FROM core.seeker_profiles WHERE user_id = (SELECT id FROM core.users WHERE email = ?)", admin.email());
        jdbc.update("UPDATE core.users SET role = 'ADMIN' WHERE email = ?", admin.email());
        return login(admin);
    }

    /** Smallest valid PNG, generated so the magic-byte check sees a real image. */
    protected static byte[] tinyPng() throws java.io.IOException {
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    protected int auditCount(String action) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM core.audit_logs WHERE action = ?", Integer.class, action);
        return n == null ? 0 : n;
    }
}
