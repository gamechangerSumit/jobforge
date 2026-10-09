package com.jobforge.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobforge.backend.support.ApiIntegrationTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 401 / 403 / ownership negatives for every Phase 1 endpoint (CLAUDE.md §5). Requires Docker. */
class AuthorizationIT extends ApiIntegrationTestSupport {

    @Autowired PasswordEncoder encoder;

    @Test
    void anonymousCallersGet401OnEveryProtectedEndpoint() throws Exception {
        for (String path : new String[] {"/users/me", "/seekers/me/profile", "/recruiters/me", "/auth/me", "/admin/users"}) {
            mvc.perform(get(API + path)).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("AUTH_UNAUTHENTICATED"));
        }
        mvc.perform(patch(API + "/users/me").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(put(API + "/seekers/me/profile").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(put(API + "/recruiters/me").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
    }

    @Test
    void roleBoundariesAreEnforced() throws Exception {
        Session seeker = ready(register("JOB_SEEKER"));
        Session recruiter = ready(register("RECRUITER"));

        mvc.perform(authedGet("/recruiters/me", seeker)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));
        mvc.perform(authedGet("/seekers/me/profile", recruiter)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));
        mvc.perform(authed(put(API + "/seekers/me/profile").contentType(MediaType.APPLICATION_JSON).content("{}"), recruiter))
                .andExpect(status().isForbidden());
        mvc.perform(authed(put(API + "/recruiters/me").contentType(MediaType.APPLICATION_JSON).content("{}"), seeker))
                .andExpect(status().isForbidden());
        mvc.perform(authedGet("/admin/users", seeker)).andExpect(status().isForbidden());
        mvc.perform(authedGet("/admin/users", recruiter)).andExpect(status().isForbidden());
    }

    @Test
    void adminPassesUrlRulesButHasNoSeekerOrRecruiterProfile() throws Exception {
        Account admin = createAdmin();
        Session s = login(admin);
        mvc.perform(authedGet("/seekers/me/profile", s)).andExpect(status().isForbidden());
        mvc.perform(authedGet("/recruiters/me", s)).andExpect(status().isForbidden());
        mvc.perform(authedGet("/admin/users", s)).andExpect(status().isOk()); // ADMIN passes the URL rule and AdminUserController exists
        mvc.perform(authedGet("/users/me", s)).andExpect(status().isOk()).andExpect(jsonPath("$.data.role").value("ADMIN"));
    }

    @Test
    void meEndpointsOnlyEverExposeTheCallersOwnData() throws Exception {
        Account a = register("JOB_SEEKER");
        Account b = register("JOB_SEEKER");
        Session sa = ready(a);
        Session sb = ready(b);
        UUID idA = jdbc.queryForObject("SELECT id FROM core.users WHERE email = ?", UUID.class, a.email());
        UUID idB = jdbc.queryForObject("SELECT id FROM core.users WHERE email = ?", UUID.class, b.email());
        mvc.perform(authedGet("/seekers/me/profile", sa)).andExpect(jsonPath("$.data.userId").value(idA.toString()));
        mvc.perform(authedGet("/seekers/me/profile", sb)).andExpect(jsonPath("$.data.userId").value(idB.toString()));
        mvc.perform(authedGet("/users/me", sa)).andExpect(jsonPath("$.data.email").value(a.email()));
        assertThat(idA).isNotEqualTo(idB);
    }

    private Account createAdmin() {
        String id = unique();
        Account admin = new Account("admin" + id + "@example.test", "Ad1" + unique(), "adm_" + id, "ADMIN");
        jdbc.update("""
                INSERT INTO core.users (id, email, password_hash, role, status, first_name, last_name, handle, email_verified_at)
                VALUES (?, ?, ?, 'ADMIN', 'ACTIVE', 'Ad', 'Min', ?, now())
                """, UUID.randomUUID(), admin.email(), encoder.encode(admin.password()), admin.handle());
        return admin;
    }
}
