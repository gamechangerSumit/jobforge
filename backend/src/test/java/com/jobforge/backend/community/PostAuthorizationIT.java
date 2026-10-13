package com.jobforge.backend.community;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Authorization matrix for the community endpoints (CLAUDE.md: every endpoint has 401/403/IDOR coverage).
 * Anonymous -> 401; non-author edit/delete -> 403; hidden/missing/deleted content -> 404 for outsiders; ADMIN may delete.
 * Requires Docker. NOT RUN in the authoring session.
 */
class PostAuthorizationIT extends CommunityTestBase {

    @Test
    void everyCommunityEndpointRequiresAuthentication() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(get(API + "/community/posts")).andExpect(status().isUnauthorized());
        mvc.perform(get(API + "/community/saved")).andExpect(status().isUnauthorized());
        mvc.perform(get(API + "/community/posts/" + id)).andExpect(status().isUnauthorized());
        mvc.perform(postJson("/community/posts", Map.of("type", "DISCUSSION", "body", "x"))).andExpect(status().isUnauthorized());
        mvc.perform(patch(API + "/community/posts/" + id).contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(API + "/community/posts/" + id)).andExpect(status().isUnauthorized());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(API + "/community/posts/" + id + "/like")).andExpect(status().isUnauthorized());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(API + "/community/posts/" + id + "/save")).andExpect(status().isUnauthorized());
        mvc.perform(postJson("/community/posts/" + id + "/share", Map.of("channel", "COPY_LINK"))).andExpect(status().isUnauthorized());
        mvc.perform(get(API + "/community/posts/" + id + "/comments")).andExpect(status().isUnauthorized());
        mvc.perform(postJson("/community/posts/" + id + "/comments", Map.of("body", "x"))).andExpect(status().isUnauthorized());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(API + "/users/" + id + "/follow")).andExpect(status().isUnauthorized());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(API + "/community/tags/java/follow")).andExpect(status().isUnauthorized());
        mvc.perform(get(API + "/community/tags/trending")).andExpect(status().isUnauthorized());
    }

    @Test
    void onlyTheAuthorMayEditAndOnlyAuthorOrAdminMayDelete() throws Exception {
        Session author = ready(register("JOB_SEEKER"));
        Session other = ready(register("JOB_SEEKER"));
        String post = createPost(author, "My own post");

        mvc.perform(authed(patch(API + "/community/posts/" + post).contentType("application/json")
                .content("{\"body\":\"hijack\"}"), other)).andExpect(status().isForbidden());
        mvc.perform(deleteReq("/community/posts/" + post, other)).andExpect(status().isForbidden());
        mvc.perform(authed(get(API + "/community/posts/" + post), other)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.body").value("My own post"));

        mvc.perform(authed(patch(API + "/community/posts/" + post).contentType("application/json")
                .content("{\"body\":\"edited\"}"), author)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.editedAt").isNotEmpty());
        mvc.perform(deleteReq("/community/posts/" + post, author)).andExpect(status().isNoContent());
        mvc.perform(authed(get(API + "/community/posts/" + post), author)).andExpect(status().isNotFound());
        mvc.perform(deleteReq("/community/posts/" + post, author)).andExpect(status().isNotFound());
    }

    @Test
    void adminDeletionIsAuditedAndHiddenPostsAreInvisibleToOutsiders() throws Exception {
        Session admin = adminLogin();
        Session author = ready(register("JOB_SEEKER"));
        Session other = ready(register("JOB_SEEKER"));
        String post = createPost(author, "Will be removed by admin");
        int before = auditCount("CONTENT_MODERATED");
        mvc.perform(deleteReq("/community/posts/" + post, admin)).andExpect(status().isNoContent());
        org.junit.jupiter.api.Assertions.assertEquals(before + 1, auditCount("CONTENT_MODERATED"));

        String hidden = createPost(author, "Hidden later");
        jdbc.update("UPDATE community.posts SET status = 'HIDDEN' WHERE id = ?::uuid", hidden);
        mvc.perform(authed(get(API + "/community/posts/" + hidden), other)).andExpect(status().isNotFound());
        mvc.perform(putReq("/community/posts/" + hidden + "/like", other)).andExpect(status().isNotFound());
        mvc.perform(authed(postJson("/community/posts/" + hidden + "/comments", Map.of("body", "hi")), other)).andExpect(status().isNotFound());
        mvc.perform(authed(get(API + "/community/posts/" + hidden), author)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("HIDDEN"));
    }

    @Test
    void commentOwnershipAndVisibilityAreEnforced() throws Exception {
        Session author = ready(register("JOB_SEEKER"));
        Session other = ready(register("RECRUITER"));
        String post = createPost(author, "Post with comments");
        String comment = createComment(author, post, "first", null);
        mvc.perform(authed(patch(API + "/community/comments/" + comment).contentType("application/json")
                .content("{\"body\":\"hijack\"}"), other)).andExpect(status().isForbidden());
        mvc.perform(deleteReq("/community/comments/" + comment, other)).andExpect(status().isForbidden());
        mvc.perform(deleteReq("/community/comments/" + UUID.randomUUID(), other)).andExpect(status().isNotFound());
        mvc.perform(deleteReq("/community/comments/" + comment, author)).andExpect(status().isNoContent());
    }

    @Test
    void validationFailuresReturn400WithFieldDetails() throws Exception {
        Session author = ready(register("JOB_SEEKER"));
        mvc.perform(authed(postJson("/community/posts", Map.of("type", "DISCUSSION", "body", "   ")), author)).andExpect(status().isBadRequest());
        mvc.perform(authed(postJson("/community/posts", Map.of("type", "NOPE", "body", "x")), author)).andExpect(status().isBadRequest());
        mvc.perform(authed(postJson("/community/posts", Map.of("type", "DISCUSSION", "body", "x",
                "tags", List.of("a", "b", "c", "d", "e", "f"))), author)).andExpect(status().isBadRequest());
        mvc.perform(authed(postJson("/community/posts", Map.of("type", "HIRING", "body", "x",
                "typeMetadata", Map.of("companyName", "Acme"))), author)).andExpect(status().isBadRequest());
        mvc.perform(authedGet("/community/posts?limit=500", author)).andExpect(status().isBadRequest());
        mvc.perform(authedGet("/community/posts?cursor=forged.value", author)).andExpect(status().isBadRequest());
        mvc.perform(authedGet("/community/posts?unknown=1", author)).andExpect(status().isBadRequest());
    }
}
