package com.jobforge.backend.community.app;

import com.jobforge.backend.community.domain.CommunityLimits;
import com.jobforge.backend.shared.domain.UserRole;
import com.jobforge.backend.shared.error.BusinessRuleException;
import com.jobforge.backend.shared.error.RateLimitedException;
import com.jobforge.backend.shared.security.AuthenticatedUser;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Anti-spam heuristics for community writes (API_CONTRACT 12.9.2), on top of the per-minute WRITE_COMMUNITY rate class:
 * <ul>
 *   <li>link cap: at most 3 links per post and 2 per comment (422);</li>
 *   <li>duplicate content: the same text by the same author within 24 h is rejected (422), case/space-insensitive;</li>
 *   <li>volume: 5 posts / 20 comments per 10 minutes and 30 posts / 200 comments per day (429 RATE_LIMITED with
 *       Retry-After). Soft-deleted content still counts, so deleting cannot be used to dodge the limits.</li>
 * </ul>
 * ADMIN accounts are exempt. Thresholds live in {@link CommunityLimits}.
 */
@Component
public class SpamGuard {

    private static final Pattern LINK = Pattern.compile("(?i)(?:https?://|www\\.)\\S+");

    private final CommunityRepository repository;
    private final Clock clock;

    public SpamGuard(CommunityRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    static int countLinks(String text) {
        int n = 0;
        Matcher m = LINK.matcher(text == null ? "" : text);
        while (m.find()) {
            n++;
        }
        return n;
    }

    /** New post: links, duplicate, volume. */
    public void checkNewPost(AuthenticatedUser caller, String body) {
        if (exempt(caller)) {
            return;
        }
        links(body, CommunityLimits.MAX_LINKS_POST, "post");
        Instant now = clock.instant();
        if (repository.postBodyExistsSince(caller.id(), body, now.minus(Duration.ofHours(CommunityLimits.DUPLICATE_WINDOW_HOURS)))) {
            throw new BusinessRuleException("You already posted this recently. Please do not repeat the same post.");
        }
        volume(repository.countPostsSince(caller.id(), now.minus(Duration.ofMinutes(10))), CommunityLimits.POSTS_PER_10_MIN,
                60, "You are posting too quickly. Please wait a few minutes.");
        volume(repository.countPostsSince(caller.id(), now.minus(Duration.ofDays(1))), CommunityLimits.POSTS_PER_DAY,
                3600, "You reached the daily post limit. Please try again later.");
    }

    /** Edited post body: only the link cap applies. */
    public void checkEditedPost(AuthenticatedUser caller, String body) {
        if (!exempt(caller)) {
            links(body, CommunityLimits.MAX_LINKS_POST, "post");
        }
    }

    public void checkNewComment(AuthenticatedUser caller, String body) {
        if (exempt(caller)) {
            return;
        }
        links(body, CommunityLimits.MAX_LINKS_COMMENT, "comment");
        Instant now = clock.instant();
        if (repository.commentBodyExistsSince(caller.id(), body, now.minus(Duration.ofHours(CommunityLimits.DUPLICATE_WINDOW_HOURS)))) {
            throw new BusinessRuleException("You already wrote this comment recently. Please do not repeat yourself.");
        }
        volume(repository.countCommentsSince(caller.id(), now.minus(Duration.ofMinutes(10))), CommunityLimits.COMMENTS_PER_10_MIN,
                60, "You are commenting too quickly. Please wait a few minutes.");
        volume(repository.countCommentsSince(caller.id(), now.minus(Duration.ofDays(1))), CommunityLimits.COMMENTS_PER_DAY,
                3600, "You reached the daily comment limit. Please try again later.");
    }

    public void checkEditedComment(AuthenticatedUser caller, String body) {
        if (!exempt(caller)) {
            links(body, CommunityLimits.MAX_LINKS_COMMENT, "comment");
        }
    }

    private static boolean exempt(AuthenticatedUser caller) {
        return caller.role() == UserRole.ADMIN;
    }

    private static void links(String body, int max, String noun) {
        if (countLinks(body) > max) {
            throw new BusinessRuleException("A " + noun + " can contain at most " + max + " links.");
        }
    }

    private static void volume(int count, int limit, long retryAfterSeconds, String message) {
        if (count >= limit) {
            throw new RateLimitedException(message, retryAfterSeconds);
        }
    }
}
