package com.jobforge.backend.profile;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobforge.backend.support.ApiIntegrationTestSupport;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** Education/experience PATCH semantics (clearEndDate) and completeness recalculation. Requires Docker. */
class SeekerHistoryPatchIT extends ApiIntegrationTestSupport {

    private String body(Object o) throws Exception {
        return json.writeValueAsString(o);
    }

    @Test
    void clearEndDateRemovesOnlyWhenRequested() throws Exception {
        Session s = ready(register("JOB_SEEKER"));
        String created = mvc.perform(authed(postJson("/seekers/me/education", Map.of(
                        "institution", "State University", "degree", "BSc", "startDate", "2018-06-01", "endDate", "2022-05-31")), s))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = json.readTree(created).at("/data/id").asText();

        // null/absent endDate keeps the stored value
        mvc.perform(authed(patch(API + "/seekers/me/education/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("grade", "A"))), s))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.endDate").value("2022-05-31"));

        // clearEndDate=true removes it
        mvc.perform(authed(patch(API + "/seekers/me/education/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("clearEndDate", true))), s))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.endDate").doesNotExist());
    }

    @Test
    void completenessGrowsWithEducationAndExperience() throws Exception {
        Session s = ready(register("JOB_SEEKER"));
        mvc.perform(authedGet("/seekers/me/profile", s)).andExpect(jsonPath("$.data.completenessScore").value(0));
        mvc.perform(authed(postJson("/seekers/me/education", Map.of(
                        "institution", "State University", "degree", "BSc", "startDate", "2018-06-01")), s))
                .andExpect(status().isCreated());
        mvc.perform(authedGet("/seekers/me/profile", s)).andExpect(jsonPath("$.data.completenessScore").value(10));
        mvc.perform(authed(postJson("/seekers/me/experience", Map.of(
                        "title", "Engineer", "companyName", "Acme", "startDate", "2022-06-01", "current", true)), s))
                .andExpect(status().isCreated());
        mvc.perform(authedGet("/seekers/me/profile", s)).andExpect(jsonPath("$.data.completenessScore").value(20));
    }

    @Test
    void otherUsersCannotPatchSomeoneElsesEntry() throws Exception {
        Session owner = ready(register("JOB_SEEKER"));
        String created = mvc.perform(authed(postJson("/seekers/me/education", Map.of(
                        "institution", "State University", "degree", "BSc", "startDate", "2018-06-01")), owner))
                .andReturn().getResponse().getContentAsString();
        String id = json.readTree(created).at("/data/id").asText();
        Session other = ready(register("JOB_SEEKER"));
        mvc.perform(authed(patch(API + "/seekers/me/education/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("grade", "B"))), other))
                .andExpect(status().isNotFound());
    }
}
