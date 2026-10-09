package com.jobforge.backend.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobforge.backend.support.ApiIntegrationTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

/** PUT /users/me/avatar and the public GET /users/{id}/avatar (API_CONTRACT 12.12). */
class AvatarIT extends ApiIntegrationTestSupport {

    private MockMultipartHttpServletRequestBuilder upload(byte[] bytes, String type) {
        return multipart(HttpMethod.PUT, API + "/users/me/avatar").file(new MockMultipartFile("file", "a", type, bytes));
    }

    @Test
    void anonymousCannotUpload() throws Exception {
        mvc.perform(upload(tinyPng(), "image/png")).andExpect(status().isUnauthorized());
    }

    @Test
    void uploadPopulatesAvatarUrlAndPublicGetServesTheImage() throws Exception {
        Session session = ready(register("JOB_SEEKER"));
        String me = mvc.perform(authed(upload(tinyPng(), "image/png"), session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.avatarUrl").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String userId = json.readTree(me).at("/data/id").asText();

        mvc.perform(get(API + "/users/" + userId + "/avatar")) // public: no token
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"));
    }

    @Test
    void nonImageBytesAreRejectedEvenWithAnImageContentType() throws Exception {
        Session session = ready(register("JOB_SEEKER"));
        mvc.perform(authed(upload("<script>alert(1)</script>".getBytes(), "image/png"), session))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void avatarOfUnknownUserIsNotFound() throws Exception {
        mvc.perform(get(API + "/users/" + UUID.randomUUID() + "/avatar")).andExpect(status().isNotFound());
    }
}
