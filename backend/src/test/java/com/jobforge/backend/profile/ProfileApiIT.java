package com.jobforge.backend.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobforge.backend.support.ApiIntegrationTestSupport;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** /seekers/me/profile, /recruiters/me, /users/me (API_CONTRACT §12.2–12.3). Requires Docker. */
class ProfileApiIT extends ApiIntegrationTestSupport {

    private MockHttpServletRequestBuilder putProfile(Session s, Object body, String ifMatch) throws Exception {
        MockHttpServletRequestBuilder b = authed(put(API + "/seekers/me/profile"), s)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        return ifMatch == null ? b : b.header("If-Match", ifMatch);
    }

    private Map<String, Object> fullProfile() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("headline", "Backend engineer");
        body.put("summary", "Ten years of JVM.");
        body.put("phone", "+919876543210");
        body.put("location", Map.of("city", "Nagpur", "state", "MH", "country", "IN"));
        body.put("currentTitle", "Senior Engineer");
        body.put("yearsExperience", 10.5);
        body.put("expectedSalary", Map.of("min", 2000000, "max", 3000000, "currency", "INR", "period", "YEAR"));
        body.put("noticePeriodDays", 30);
        body.put("openToWork", false);
        body.put("visibility", "PUBLIC");
        body.put("links", Map.of("linkedin", "https://linkedin.com/in/x", "github", "https://github.com/x"));
        return body;
    }

    // ---------------------------------------------------------------- seeker

    @Test
    void newSeekerHasEmptyDefaultProfile() throws Exception {
        Session s = ready(register("JOB_SEEKER"));
        mvc.perform(authedGet("/seekers/me/profile", s))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"0\""))
                .andExpect(jsonPath("$.data.version").value(0))
                .andExpect(jsonPath("$.data.openToWork").value(true))
                .andExpect(jsonPath("$.data.visibility").value("RECRUITERS_ONLY"))
                .andExpect(jsonPath("$.data.completenessScore").value(0))
                .andExpect(jsonPath("$.data.skills").isEmpty());
    }

    @Test
    void putReplacesEditableSetAndVersionsOptimistically() throws Exception {
        Session s = ready(register("JOB_SEEKER"));
        mvc.perform(putProfile(s, fullProfile(), "\"0\""))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"1\""))
                .andExpect(jsonPath("$.data.headline").value("Backend engineer"))
                .andExpect(jsonPath("$.data.location.country").value("IN"))
                .andExpect(jsonPath("$.data.expectedSalary.max").value(3000000))
                .andExpect(jsonPath("$.data.expectedSalary.period").value("YEAR"))
                .andExpect(jsonPath("$.data.yearsExperience").value(10.5))
                .andExpect(jsonPath("$.data.openToWork").value(false))
                .andExpect(jsonPath("$.data.visibility").value("PUBLIC"))
                .andExpect(jsonPath("$.data.version").value(1));

        mvc.perform(putProfile(s, fullProfile(), "\"0\"")) // stale
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("STALE_VERSION"));
        mvc.perform(putProfile(s, Map.of("headline", "Only headline"), null)) // replace: everything else is cleared/defaulted
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(2))
                .andExpect(jsonPath("$.data.summary").doesNotExist())
                .andExpect(jsonPath("$.data.location").doesNotExist())
                .andExpect(jsonPath("$.data.openToWork").value(true))
                .andExpect(jsonPath("$.data.visibility").value("RECRUITERS_ONLY"));
        mvc.perform(putProfile(s, Map.of(), "abc")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void profileValidationUsesContractLimits() throws Exception {
        Session s = ready(register("JOB_SEEKER"));
        Map<String, Object> bad = new LinkedHashMap<>();
        bad.put("headline", "x".repeat(121));
        bad.put("phone", "12345");
        bad.put("links", Map.of("linkedin", "http://insecure.example"));
        bad.put("noticePeriodDays", 400);
        bad.put("expectedSalary", Map.of("currency", "inr"));
        mvc.perform(putProfile(s, bad, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.details[?(@.field=='headline')].code", hasItem("SIZE")))
                .andExpect(jsonPath("$.error.details[?(@.field=='phone')].code", hasItem("PATTERN")))
                .andExpect(jsonPath("$.error.details[?(@.field=='links.linkedin')].code", hasItem("PATTERN")))
                .andExpect(jsonPath("$.error.details[?(@.field=='noticePeriodDays')].code", hasItem("MAX")))
                .andExpect(jsonPath("$.error.details[?(@.field=='expectedSalary.currency')].code", hasItem("PATTERN")));

        mvc.perform(putProfile(s, Map.of("expectedSalary", Map.of("min", 10, "max", 5)), null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("expectedSalary.max"));
        mvc.perform(putProfile(s, Map.of("visibility", "EVERYONE"), null))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.details[0].code").value("INVALID_ENUM"));
        mvc.perform(putProfile(s, Map.of("completenessScore", 100), null))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.details[0].code").value("UNKNOWN_FIELD"));
    }

    // ---------------------------------------------------------------- recruiter

    @Test
    void recruiterProfileReadUpdateAndReadOnlyApproval() throws Exception {
        Account a = register("RECRUITER");
        Session s = ready(a);
        mvc.perform(authedGet("/recruiters/me", s)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.approvalStatus").value("PENDING"));
        mvc.perform(authed(put(API + "/recruiters/me"), s).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobTitle\":\"Talent Partner\",\"phone\":\"+14155550123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jobTitle").value("Talent Partner"))
                .andExpect(jsonPath("$.data.approvalStatus").value("PENDING"));
        mvc.perform(authed(put(API + "/recruiters/me"), s).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approvalStatus\":\"APPROVED\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.details[0].code").value("UNKNOWN_FIELD"));
        mvc.perform(authed(put(API + "/recruiters/me"), s).contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"nope\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.details[0].code").value("PATTERN"));

        jdbc.update("UPDATE core.recruiter_profiles SET approval_status = 'APPROVED' WHERE user_id = (SELECT id FROM core.users WHERE email = ?)", a.email());
        mvc.perform(authedGet("/auth/me", s)).andExpect(jsonPath("$.data.recruiterApproved").value(true));
    }

    // ---------------------------------------------------------------- users/me

    @Test
    void usersMeReadAndPatch() throws Exception {
        Account a = register("JOB_SEEKER");
        Account other = register("JOB_SEEKER");
        Session s = ready(a);
        mvc.perform(authedGet("/users/me", s)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(a.email()))
                .andExpect(content().string(not(containsString("password"))));

        String newHandle = "n_" + unique();
        mvc.perform(authed(patch(API + "/users/me"), s).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Grace\",\"handle\":\"" + newHandle + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.firstName").value("Grace"))
                .andExpect(jsonPath("$.data.lastName").value("User")) // untouched
                .andExpect(jsonPath("$.data.handle").value(newHandle));

        mvc.perform(authed(patch(API + "/users/me"), s).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"handle\":\"" + other.handle() + "\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("HANDLE_TAKEN"));
        mvc.perform(authed(patch(API + "/users/me"), s).contentType(MediaType.APPLICATION_JSON).content("{\"handle\":\"Bad Handle\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[?(@.field=='handle')].code", hasItem("PATTERN")));
        mvc.perform(authed(patch(API + "/users/me"), s).contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.details[0].code").value("UNKNOWN_FIELD"));
        assertThat(jdbc.queryForObject("SELECT role FROM core.users WHERE email = ?", String.class, a.email())).isEqualTo("JOB_SEEKER");
        assertThat(List.of(newHandle)).isNotEmpty();
    }
}
