package com.jobforge.backend.community;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Community behaviour: interactions and counters, comments, feeds and cursors, follows, tags, mentions and the
 * report/moderation integration (POST/COMMENT targets, HIDE_CONTENT, REMOVE_CONTENT). Requires Docker.
 * Written but NOT executed in the authoring session.
 */
class CommunityFlowIT extends CommunityTestBase {

    @Test
    void likeAndSaveAreIdempotentAndCountersMatchRows() throws Exception {
        Session author = ready(register("JOB_SEEKER"));
        Session fan = ready(register("RECRUITER"));
        String post = createPost(author, "Counters please");

        for (int i = 0; i < 3; i++) {
            mvc.perform(putReq("/community/posts/" + post + "/like", fan)).andExpect(status().isNoContent());
            mvc.perform(putReq("/community/posts/" + post + "/save", fan)).andExpect(status().isNoContent());
        }
        assertEquals(1, intAt("SELECT like_count FROM community.posts WHERE id = ?::uuid", post));
        assertEquals(1, intAt("SELECT save_count FROM community.posts WHERE id = ?::uuid", post));
        mvc.perform(authedGet("/community/posts/" + post, fan)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.counts.likes").value(1))
                .andExpect(jsonPath("$.data.viewer.liked").value(true))
                .andExpect(jsonPath("$.data.viewer.saved").value(true));
        mvc.perform(authedGet("/community/saved", fan)).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1));

        for (int i = 0; i < 2; i++) {
            mvc.perform(deleteReq("/community/posts/" + post + "/like", fan)).andExpect(status().isNoContent());
            mvc.perform(deleteReq("/community/posts/" + post + "/save", fan)).andExpect(status().isNoContent());
        }
        assertEquals(0, intAt("SELECT like_count FROM community.posts WHERE id = ?::uuid", post));
        assertEquals(0, intAt("SELECT save_count FROM community.posts WHERE id = ?::uuid", post));
    }

    @Test
    void shareReturnsALinkAndCountsOnce() throws Exception {
        Session author = ready(register("JOB_SEEKER"));
        String post = createPost(author, "Share me");
        mvc.perform(authed(postJson("/community/posts/" + post + "/share", Map.of("channel", "COPY_LINK")), author))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.url").value(org.hamcrest.Matchers.endsWith("/community/posts/" + post)));
        assertEquals(1, intAt("SELECT share_count FROM community.posts WHERE id = ?::uuid", post));
        mvc.perform(authed(postJson("/community/posts/" + post + "/share", Map.of("channel", "FAX")), author)).andExpect(status().isBadRequest());
    }

    @Test
    void commentsRepliesDepthAndCountersWork() throws Exception {
        Session author = ready(register("JOB_SEEKER"));
        Session other = ready(register("RECRUITER"));
        String post = createPost(author, "Discuss");
        String top = createComment(other, post, "top level", null);
        String reply = createComment(author, post, "a reply", top);
        assertEquals(2, intAt("SELECT comment_count FROM community.posts WHERE id = ?::uuid", post));

        // replies cannot be nested further (max depth 2)
        mvc.perform(authed(postJson("/community/posts/" + post + "/comments", Map.of("body", "too deep", "parentId", reply)), other))
                .andExpect(status().isUnprocessableEntity());
        // parent must belong to the same post
        String otherPost = createPost(author, "Another post");
        mvc.perform(authed(postJson("/community/posts/" + otherPost + "/comments", Map.of("body", "x", "parentId", top)), other))
                .andExpect(status().isNotFound());

        mvc.perform(authedGet("/community/posts/" + post + "/comments", author)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1)).andExpect(jsonPath("$.data[0].counts.replies").value(1));
        mvc.perform(authedGet("/community/posts/" + post + "/comments?parentId=" + top, author)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(reply));

        mvc.perform(putReq("/community/comments/" + top + "/like", author)).andExpect(status().isNoContent());
        mvc.perform(putReq("/community/comments/" + top + "/like", author)).andExpect(status().isNoContent());
        assertEquals(1, intAt("SELECT like_count FROM community.comments WHERE id = ?::uuid", top));

        mvc.perform(deleteReq("/community/comments/" + reply, author)).andExpect(status().isNoContent());
        assertEquals(1, intAt("SELECT comment_count FROM community.posts WHERE id = ?::uuid", post));
    }

    @Test
    void feedPaginatesWithSignedCursorsWithoutDuplicates() throws Exception {
        Account authorAccount = register("JOB_SEEKER");
        Session author = ready(authorAccount);
        for (int i = 0; i < 5; i++) createPost(author, "feed item " + i);
        String path = "/community/posts?authorId=" + userId(authorAccount) + "&limit=2";
        List<String> seen = new ArrayList<>();
        String cursor = null;
        boolean hasNext = true;
        int pages = 0;
        while (hasNext && pages++ < 5) {
            var node = json.readTree(mvc.perform(authedGet(path + (cursor == null ? "" : "&cursor=" + cursor), author))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            node.at("/data").forEach(n -> seen.add(n.get("id").asText()));
            hasNext = node.at("/meta/cursor/hasNext").asBoolean();
            cursor = node.at("/meta/cursor/next").isNull() ? null : node.at("/meta/cursor/next").asText();
        }
        assertEquals(5, seen.size());
        assertEquals(5, seen.stream().distinct().count());
    }

    @Test
    void followingFeedShowsFollowedAuthorsAndFollowIsIdempotent() throws Exception {
        Account followeeAccount = register("JOB_SEEKER");
        Session followee = ready(followeeAccount);
        Account followerAccount = register("JOB_SEEKER");
        Session follower = ready(followerAccount);
        String followeeId = userId(followeeAccount).toString();
        String post = createPost(followee, "from followee");
        createPost(follower, "my own");

        mvc.perform(authedGet("/community/posts?feed=following", follower)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
        mvc.perform(putReq("/users/" + followeeId + "/follow", follower)).andExpect(status().isNoContent());
        mvc.perform(putReq("/users/" + followeeId + "/follow", follower)).andExpect(status().isNoContent());
        assertEquals(1, intAt("SELECT count(*) FROM community.user_follows WHERE followee_id = ?::uuid", followeeId));
        mvc.perform(authedGet("/community/posts?feed=following", follower)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1)).andExpect(jsonPath("$.data[0].id").value(post))
                .andExpect(jsonPath("$.data[0].viewer.following").value(true));
        mvc.perform(putReq("/users/" + userId(followerAccount) + "/follow", follower)).andExpect(status().isUnprocessableEntity());
        mvc.perform(putReq("/users/" + java.util.UUID.randomUUID() + "/follow", follower)).andExpect(status().isNotFound());
        mvc.perform(deleteReq("/users/" + followeeId + "/follow", follower)).andExpect(status().isNoContent());
        mvc.perform(authedGet("/community/posts?feed=following", follower)).andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void tagsAreNormalisedFollowableAndSearchable() throws Exception {
        Session author = ready(register("JOB_SEEKER"));
        String tag = "tag" + System.nanoTime() % 100000;
        String post = createPost(author, Map.of("type", "TECHNICAL", "body", "tagged", "tags", List.of(tag.toUpperCase(), tag)));
        mvc.perform(authedGet("/community/posts/" + post, author)).andExpect(jsonPath("$.data.tags.length()").value(1))
                .andExpect(jsonPath("$.data.tags[0]").value(tag));
        mvc.perform(authedGet("/community/posts?tag=" + tag, author)).andExpect(jsonPath("$.data[0].id").value(post));
        mvc.perform(authedGet("/community/tags?q=" + tag.substring(0, 4), author)).andExpect(status().isOk());
        mvc.perform(putReq("/community/tags/" + tag + "/follow", author)).andExpect(status().isNoContent());
        mvc.perform(putReq("/community/tags/does-not-exist-xyz/follow", author)).andExpect(status().isNotFound());
        mvc.perform(authedGet("/community/tags/trending", author)).andExpect(status().isOk());
    }

    @Test
    void mentionsStoreOnlyActiveUsersAndNeverSelf() throws Exception {
        Account mentionedAccount = register("JOB_SEEKER");
        ready(mentionedAccount);
        Account authorAccount = register("JOB_SEEKER");
        Session author = ready(authorAccount);
        String post = createPost(author, Map.of("type", "DISCUSSION",
                "body", "thanks @" + mentionedAccount.handle() + " and @" + authorAccount.handle() + " and @nobody_here_xyz"));
        assertEquals(1, intAt("SELECT count(*) FROM community.mentions WHERE source_id = ?::uuid", post));
        assertEquals(1, intAt("SELECT count(*) FROM community.mentions WHERE source_id = ?::uuid AND mentioned_user_id = ?",
                post, userId(mentionedAccount)));
    }

    @Test
    void reportingAndModeratingPostsAndComments() throws Exception {
        Session admin = adminLogin();
        Session author = ready(register("JOB_SEEKER"));
        Session reporter = ready(register("JOB_SEEKER"));
        String post = createPost(author, Map.of("type", "DISCUSSION", "title", "Spammy", "body", "buy now"));
        String comment = createComment(author, post, "spam comment", null);

        // own content cannot be reported, others can, once each
        mvc.perform(authed(postJson("/reports", Map.of("targetType", "POST", "targetId", post, "reason", "SPAM")), author))
                .andExpect(status().isUnprocessableEntity());
        String postReport = json.readTree(mvc.perform(authed(postJson("/reports", Map.of("targetType", "POST", "targetId", post,
                "reason", "SPAM")), reporter)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .at("/data/id").asText();
        mvc.perform(authed(postJson("/reports", Map.of("targetType", "POST", "targetId", post, "reason", "SPAM")), reporter))
                .andExpect(status().isConflict());
        String commentReport = json.readTree(mvc.perform(authed(postJson("/reports", Map.of("targetType", "COMMENT", "targetId",
                comment, "reason", "HARASSMENT")), reporter)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .at("/data/id").asText();

        // HIDE_CONTENT hides the comment and decrements the counter; SUSPEND_USER is not a content action
        mvc.perform(authed(postJson("/admin/reports/" + commentReport + "/resolve",
                Map.of("action", "SUSPEND_USER", "reason", "not applicable")), admin)).andExpect(status().isUnprocessableEntity());
        mvc.perform(authed(postJson("/admin/reports/" + commentReport + "/resolve",
                Map.of("action", "HIDE_CONTENT", "reason", "harassing comment")), admin)).andExpect(status().isOk());
        assertEquals("HIDDEN", jdbc.queryForObject("SELECT status FROM community.comments WHERE id = ?::uuid", String.class, comment));
        assertEquals(0, intAt("SELECT comment_count FROM community.posts WHERE id = ?::uuid", post));
        mvc.perform(authedGet("/community/posts/" + post + "/comments", reporter)).andExpect(jsonPath("$.data.length()").value(0));

        // REMOVE_CONTENT on the post removes it from every public view
        mvc.perform(authed(postJson("/admin/reports/" + postReport + "/resolve",
                Map.of("action", "REMOVE_CONTENT", "reason", "spam post content")), admin)).andExpect(status().isOk());
        assertEquals("REMOVED", jdbc.queryForObject("SELECT status FROM community.posts WHERE id = ?::uuid", String.class, post));
        mvc.perform(authedGet("/community/posts/" + post, reporter)).andExpect(status().isNotFound());
        assertEquals(1, intAt("SELECT count(*) FROM core.audit_logs WHERE action = 'CONTENT_MODERATED' AND entity_id = ?::uuid", post));
        // a removed target can no longer be reported
        Session another = ready(register("JOB_SEEKER"));
        mvc.perform(authed(postJson("/reports", Map.of("targetType", "POST", "targetId", post, "reason", "SPAM")), another))
                .andExpect(status().isNotFound());
    }
}
