package com.jobforge.backend.company;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobforge.backend.support.ApiIntegrationTestSupport;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

/** PUT /companies/{id}/logo (owner only) and public GET /companies/{id}/logo. */
class CompanyLogoIT extends ApiIntegrationTestSupport {

    private MockMultipartHttpServletRequestBuilder upload(String companyId, byte[] bytes) {
        return multipart(HttpMethod.PUT, API + "/companies/" + companyId + "/logo")
                .file(new MockMultipartFile("file", "logo.png", "image/png", bytes));
    }

    private String createCompany(Session recruiter) throws Exception {
        String body = mvc.perform(authed(postJson("/companies", Map.of("name", "Logo Co " + unique())), recruiter))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).at("/data/id").asText();
    }

    @Test
    void authorizationNegatives() throws Exception {
        Session owner = ready(register("RECRUITER"));
        String companyId = createCompany(owner);

        mvc.perform(upload(companyId, tinyPng())).andExpect(status().isUnauthorized());
        mvc.perform(authed(upload(companyId, tinyPng()), ready(register("JOB_SEEKER")))).andExpect(status().isForbidden());
        // another recruiter must not learn the company exists (IDOR -> 404)
        mvc.perform(authed(upload(companyId, tinyPng()), ready(register("RECRUITER")))).andExpect(status().isNotFound());
    }

    @Test
    void ownerUploadsAndLogoIsPubliclyReadableOnceVerified() throws Exception {
        Session owner = ready(register("RECRUITER"));
        String companyId = createCompany(owner);
        mvc.perform(authed(upload(companyId, tinyPng()), owner)).andExpect(status().isOk());

        jdbc.update("UPDATE core.companies SET verification_status = 'VERIFIED' WHERE id = ?::uuid", companyId);
        mvc.perform(get(API + "/companies/" + companyId + "/logo"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"));
    }

    @Test
    void invalidImageIsRejected() throws Exception {
        Session owner = ready(register("RECRUITER"));
        String companyId = createCompany(owner);
        mvc.perform(authed(upload(companyId, "not an image".getBytes()), owner)).andExpect(status().is4xxClientError());
    }
}
