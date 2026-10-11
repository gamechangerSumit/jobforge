package com.jobforge.backend.community;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobforge.backend.community.app.AccountCleanupService;
import com.jobforge.backend.community.facade.CommunityFacade;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Consistency rules added in the Session 4 fix pass: comment-tree cascade and exact comment_count, account-deletion
 * purge, and the anti-spam heuristics. Requires Docker. Written but NOT executed in the authoring session.
 */
class CommunityConsistencyIT extends CommunityTestBase {

    @Autowired AccountCleanupService cleanup;
    @Autowired CommunityFacade community;

    private int commentCount(String post) {
        return intAt("SELECT comment_count FROM community.posts WHERE id = ?::uuid", post);
    }

    @Test
    void deletingATopLevelCommentHidesItsRepliesAndRecountsExactly() throws Exception {
        Session author = ready(register("JOB_SEEKER"));
        Session other = ready(register("RECRUITER"));
        String post = createPost(author, "Thread");
        String top = createComment(other, post, "top level", null);
        String reply1 = createComment(author, post, "reply one", top);
        createComment(other, post, "reply two", top);
        String second = createComment(other, post, "another top level", null);
        assertEquals(4, commentCount(post));

        mvc.perform(deleteReq("/community/comments/" + top, other)).andExpect(status().isNoContent());
        assertEquals(1, commentCount(post)); // the parent and both replies are gone from the count
        mvc.perform(authedGet("/community/posts/" + post + "/comments?parentId=" + top, author)).andExpect(status().isNotFound());
        mvc.perform(putReq("/community/comments/" + reply1 + "/like", author)).andExpect(status().isNotFound());
        mvc.perform(deleteReq("/community/comments/" + reply1, author)).andExpect(status().isNoContent()); // the author may still delete it
        assertEquals(1, commentCount(post)); // deleting an already hidden reply must not decrement again
        mvc.perform(authedGet("/community/posts/" + post + "/comments", author)).andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(second));
    }

    @Test
    void moderationHideOfATopLevelCommentAlsoRecountsItsReplies() throws Exception {
        Session author = ready(register("JOB_SEEKER"));
        Session other = ready(register("RECRUITER"));
        String post = createPost(author, "Moderated thread");
        String top = createComment(other, post, "top", null);
        createComment(author, post, "reply", top);
        assertEquals(2, commentCount(post));
        community.hideComment(java.util.UUID.fromString(top));
        assertEquals(0, commentCount(post));
        community.removeComment(java.util.UUID.fromString(top)); // HIDDEN -> REMOVED keeps the count exact
        assertEquals(0, commentCount(post));
    }

    @Test
    void accountDeletionPurgesLikesSavesFollowsAndFixesCounters() throws Exception {
        Account authorAccount = register("JOB_SEEKER");
        Session author = ready(authorAccount);
        Account fanAccount = register("RECRUITER");
        Session fan = ready(fanAccount);
        String post = createPost(author, "Popular");
        String comment = createComment(author, post, "a comment", null);
        mvc.perform(putReq("/community/posts/" + post + "/like", fan)).andExpect(status().isNoContent());
        mvc.perform(putReq("/community/posts/" + post + "/save", fan)).andExpect(status().isNoContent());
        mvc.perform(putReq("/community/comments/" + comment + "/like", fan)).andExpect(status().isNoContent());
        mvc.perform(putReq("/users/" + userId(authorAccount) + "/follow", fan)).andExpect(status().isNoContent());
        String fanComment = createComment(fan, post, "fan comment", null);
        assertEquals(2, commentCount(post));

        jdbc.update("UPDATE core.users SET status = 'DELETED' WHERE id = ?", userId(fanAccount)); // what anonymize does
        cleanup.purge(userId(fanAccount));
        cleanup.purge(userId(fanAccount)); // idempotent

        assertEquals(0, intAt("SELECT like_count FROM community.posts WHERE id = ?::uuid", post));
        assertEquals(0, intAt("SELECT save_count FROM community.posts WHERE id = ?::uuid", post));
        assertEquals(0, intAt("SELECT like_count FROM community.comments WHERE id = ?::uuid", comment));
        assertEquals(0, intAt("SELECT count(*) FROM community.user_follows WHERE follower_id = ?", userId(fanAccount)));
        assertEquals(1, commentCount(post)); // the deleted account's comment no longer counts
        assertEquals(1, intAt("SELECT count(*) FROM community.comments WHERE id = ?::uuid", fanComment)); // row kept, just invisible
    }

    @Test
    void antiSpamRejectsLinkFloodsDuplicatesAndBursts() throws Exception {
        Session author = ready(register("JOB_SEEKER"));
        mvc.perform(authed(postJson("/community/posts", Map.of("type", "DISCUSSION",
                "body", "https://1.example https://2.example https://3.example https://4.example")), author))
                .andExpect(status().isUnprocessableEntity());
        createPost(author, "Original announcement");
        mvc.perform(authed(postJson("/community/posts", Map.of("type", "DISCUSSION", "body", "  original ANNOUNCEMENT ")), author))
                .andExpect(status().isUnprocessableEntity());
        for (int i = 1; i < 5; i++) createPost(author, "distinct post number " + i);
        mvc.perform(authed(postJson("/community/posts", Map.of("type", "DISCUSSION", "body", "one post too many")), author))
                .andExpect(status().isTooManyRequests()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().exists("Retry-After"));

        Session commenter = ready(register("JOB_SEEKER"));
        String post = createPost(commenter, "Comment target");
        createComment(commenter, post, "first comment", null);
        mvc.perform(authed(postJson("/community/posts/" + post + "/comments", Map.of("body", "First comment")), commenter))
                .andExpect(status().isUnprocessableEntity());
    }
}
