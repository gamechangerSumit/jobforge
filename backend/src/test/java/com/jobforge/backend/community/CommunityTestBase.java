package com.jobforge.backend.community;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobforge.backend.support.ApiIntegrationTestSupport;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Shared helpers for the community ITs. Requires Docker; NOT RUN in the authoring session. */
abstract class CommunityTestBase extends ApiIntegrationTestSupport {

    protected UUID userId(Account account) {
        return jdbc.queryForObject("SELECT id FROM core.users WHERE lower(email) = lower(?)", UUID.class, account.email());
    }

    protected String createPost(Session author, String body) throws Exception {
        return createPost(author, Map.of("type", "DISCUSSION", "body", body, "tags", List.of("java")));
    }

    protected String createPost(Session author, Map<String, Object> request) throws Exception {
        return json.readTree(mvc.perform(authed(postJson("/community/posts", request), author))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).at("/data/id").asText();
    }

    protected String createComment(Session author, String postId, String body, String parentId) throws Exception {
        Map<String, Object> request = parentId == null ? Map.of("body", body) : Map.of("body", body, "parentId", parentId);
        return json.readTree(mvc.perform(authed(postJson("/community/posts/" + postId + "/comments", request), author))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).at("/data/id").asText();
    }

    protected MockHttpServletRequestBuilder putReq(String path, Session s) {
        return authed(put(API + path), s);
    }

    protected MockHttpServletRequestBuilder deleteReq(String path, Session s) {
        return authed(delete(API + path), s);
    }

    protected int intAt(String sql, Object... args) {
        Integer v = jdbc.queryForObject(sql, Integer.class, args);
        return v == null ? 0 : v;
    }
}
