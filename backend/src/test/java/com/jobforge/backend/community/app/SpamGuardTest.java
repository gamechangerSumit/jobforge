package com.jobforge.backend.community.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.jobforge.backend.shared.domain.UserRole;
import com.jobforge.backend.shared.error.BusinessRuleException;
import com.jobforge.backend.shared.error.RateLimitedException;
import com.jobforge.backend.shared.security.AuthenticatedUser;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** NOT RUN in the authoring session (no builds were authorized). */
class SpamGuardTest {

    private final CommunityRepository repository = mock(CommunityRepository.class);
    private final SpamGuard guard = new SpamGuard(repository, Clock.fixed(Instant.parse("2026-10-10T10:00:00Z"), ZoneOffset.UTC));
    private final UUID id = UUID.randomUUID();
    private final AuthenticatedUser user = new AuthenticatedUser(id, UserRole.JOB_SEEKER);
    private final AuthenticatedUser admin = new AuthenticatedUser(UUID.randomUUID(), UserRole.ADMIN);

    @Test
    void countsHttpAndWwwLinks() {
        assertThat(SpamGuard.countLinks("see https://a.example and http://b.example and www.c.example/x")).isEqualTo(3);
        assertThat(SpamGuard.countLinks("no links, mail me at a@b.example")).isZero();
        assertThat(SpamGuard.countLinks(null)).isZero();
    }

    @Test
    void rejectsTooManyLinksInPostsAndComments() {
        String four = "https://1.example https://2.example https://3.example https://4.example";
        assertThatThrownBy(() -> guard.checkNewPost(user, four)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> guard.checkEditedPost(user, four)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> guard.checkNewComment(user, "https://1.example https://2.example https://3.example"))
                .isInstanceOf(BusinessRuleException.class);
        assertThatCode(() -> guard.checkNewPost(user, "https://1.example https://2.example https://3.example")).doesNotThrowAnyException();
    }

    @Test
    void rejectsDuplicatePostsAndComments() {
        when(repository.postBodyExistsSince(eq(id), eq("same text"), any())).thenReturn(true);
        when(repository.commentBodyExistsSince(eq(id), eq("same comment"), any())).thenReturn(true);
        assertThatThrownBy(() -> guard.checkNewPost(user, "same text")).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> guard.checkNewComment(user, "same comment")).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void volumeLimitsAnswerRateLimitedWithRetryAfter() {
        when(repository.countPostsSince(eq(id), any())).thenReturn(5);
        assertThatThrownBy(() -> guard.checkNewPost(user, "fresh text")).isInstanceOf(RateLimitedException.class)
                .satisfies(e -> assertThat(((RateLimitedException) e).retryAfterSeconds()).isPositive());
        when(repository.countCommentsSince(eq(id), any())).thenReturn(20);
        assertThatThrownBy(() -> guard.checkNewComment(user, "fresh comment")).isInstanceOf(RateLimitedException.class);
    }

    @Test
    void administratorsAreExempt() {
        String many = "https://1.example https://2.example https://3.example https://4.example https://5.example";
        assertThatCode(() -> guard.checkNewPost(admin, many)).doesNotThrowAnyException();
        assertThatCode(() -> guard.checkNewComment(admin, many)).doesNotThrowAnyException();
    }
}
